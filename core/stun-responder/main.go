package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"flag"
	"fmt"
	"hash/crc32"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"
)

const (
	stunMagicCookie       uint32 = 0x2112A442
	stunBindingRequest    uint16 = 0x0001
	stunBindingResponse   uint16 = 0x0101
	attrMappedAddress     uint16 = 0x0001
	attrXorMappedAddress  uint16 = 0x0020
	attrResponseOrigin    uint16 = 0x802b
	attrOtherAddress      uint16 = 0x802c
	attrSoftware          uint16 = 0x8022
	attrFingerprint       uint16 = 0x8028
	fingerprintXorMask    uint32 = 0x5354554e
	defaultLocalProxyAddr string = "127.0.0.1:10809"
)

type GeoMeta struct {
	IP          string `json:"ip"`
	Country     string `json:"country"`
	CountryCode string `json:"countryCode"`
	City        string `json:"city"`
	ISP         string `json:"isp"`
	Org         string `json:"org"`
	Timezone    string `json:"timezone"`
	UpdatedAt   int64  `json:"updatedAt"`
}

var (
	mu          sync.RWMutex
	currentIP   net.IP
	currentMeta GeoMeta
	localProxy  string

	bindMu     sync.Mutex
	activeBind = make(map[string]*net.UDPConn)
	refreshMu  sync.Mutex
)

func main() {
	port := flag.Int("port", 10803, "Primary STUN UDP port (Phone OUTPUT)")
	hotspotPort := flag.Int("hotspot-port", 10805, "Hotspot STUN UDP port (Tether PREROUTING)")
	ctrlAddr := flag.String("ctrl", "127.0.0.1:10804", "HTTP control listen address")
	proxyAddr := flag.String("proxy", defaultLocalProxyAddr, "Local sing-box mixed proxy address")
	initIP := flag.String("ip", "", "Initial spoofed IPv4 address")
	flag.Parse()

	localProxy = strings.TrimSpace(*proxyAddr)
	if localProxy == "" {
		localProxy = defaultLocalProxyAddr
	}

	if *initIP != "" && *initIP != "0.0.0.0" {
		if parsed := net.ParseIP(strings.TrimSpace(*initIP)); parsed != nil {
			if v4 := parsed.To4(); v4 != nil && !v4.IsUnspecified() && !v4.IsLoopback() {
				setSpoofIP(v4, GeoMeta{IP: v4.String(), UpdatedAt: time.Now().Unix()})
			}
		}
	}

	// Bind UDP sockets on 127.0.0.1 and every active interface IPv4 address.
	// Binding per concrete IPv4 address guarantees the kernel sets the exact matching
	// source IP on WriteToUDP replies so conntrack reverse-NATs 100% of packets on both
	// Phone OUTPUT REDIRECT (127.0.0.1) and Hotspot PREROUTING REDIRECT (192.168.x.1).
	ports := []int{*port}
	if *hotspotPort > 0 && *hotspotPort != *port {
		ports = append(ports, *hotspotPort)
	}
	syncUDPListeners(ports)

	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			syncUDPListeners(ports)
		}
	}()

	go startControlServer(*ctrlAddr)
	go backgroundIPRefresher()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	closeAllListeners()
}

func syncUDPListeners(ports []int) {
	ips := collectLocalIPv4s()
	bindMu.Lock()
	defer bindMu.Unlock()

	desired := make(map[string]bool)
	for _, ipStr := range ips {
		for _, p := range ports {
			key := fmt.Sprintf("%s:%d", ipStr, p)
			desired[key] = true
			if _, exists := activeBind[key]; exists {
				continue
			}
			addr := &net.UDPAddr{IP: net.ParseIP(ipStr), Port: p}
			conn, err := net.ListenUDP("udp4", addr)
			if err != nil {
				continue
			}
			activeBind[key] = conn
			go serveUDPConn(conn, key)
		}
	}

	for key, conn := range activeBind {
		if !desired[key] {
			_ = conn.Close()
			delete(activeBind, key)
		}
	}
}

func closeAllListeners() {
	bindMu.Lock()
	defer bindMu.Unlock()
	for k, c := range activeBind {
		_ = c.Close()
		delete(activeBind, k)
	}
}

func collectLocalIPv4s() []string {
	seen := map[string]bool{"127.0.0.1": true}
	out := []string{"127.0.0.1"}

	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return out
	}
	for _, a := range addrs {
		var ip net.IP
		switch v := a.(type) {
		case *net.IPNet:
			ip = v.IP
		case *net.IPAddr:
			ip = v.IP
		}
		if ip == nil {
			continue
		}
		v4 := ip.To4()
		if v4 == nil || v4.IsUnspecified() {
			continue
		}
		s := v4.String()
		if !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	return out
}

func serveUDPConn(conn *net.UDPConn, bindKey string) {
	buf := make([]byte, 2048)
	for {
		n, clientAddr, err := conn.ReadFromUDP(buf)
		if err != nil {
			return
		}
		if n < 20 {
			continue
		}
		pkt := make([]byte, n)
		copy(pkt, buf[:n])
		go handleSTUNPacket(conn, clientAddr, pkt)
	}
}

func handleSTUNPacket(conn *net.UDPConn, clientAddr *net.UDPAddr, pkt []byte) {
	if len(pkt) < 20 {
		return
	}
	// Verify RFC 5389 magic cookie (0x2112A442) or classic RFC 3489 Binding Request
	msgType := binary.BigEndian.Uint16(pkt[0:2])
	cookie := binary.BigEndian.Uint32(pkt[4:8])
	// First two bits of every STUN message must be 0b00, and request type is Binding Request (0x0001)
	if msgType != stunBindingRequest {
		return
	}
	isRFC5389 := (cookie == stunMagicCookie)

	spoofIP := getSpoofIP()
	if spoofIP == nil {
		// Attempt immediate proxy-routed lookup if not yet cached
		refreshExitIPOnce(1800 * time.Millisecond)
		spoofIP = getSpoofIP()
	}
	if spoofIP == nil || spoofIP.IsUnspecified() || spoofIP.IsLoopback() {
		// Never leak real ISP IP or 0.0.0.0; drop so browser retries after 200ms
		return
	}

	txID := pkt[8:20]
	clientPort := uint16(clientAddr.Port)
	if clientPort == 0 {
		clientPort = 54321
	}

	resp := buildSTUNBindingResponse(txID, spoofIP.To4(), clientPort, isRFC5389)
	_, _ = conn.WriteToUDP(resp, clientAddr)
}

func buildSTUNBindingResponse(txID []byte, ip4 net.IP, port uint16, isRFC5389 bool) []byte {
	var attrs bytes.Buffer

	// 1. XOR-MAPPED-ADDRESS (0x0020)
	xorPort := port ^ uint16(stunMagicCookie>>16)
	var cookieBytes [4]byte
	binary.BigEndian.PutUint32(cookieBytes[:], stunMagicCookie)
	var xorIP [4]byte
	for i := 0; i < 4; i++ {
		xorIP[i] = ip4[i] ^ cookieBytes[i]
	}
	var xma [8]byte
	xma[0] = 0x00
	xma[1] = 0x01 // IPv4 family
	binary.BigEndian.PutUint16(xma[2:4], xorPort)
	copy(xma[4:8], xorIP[:])
	writeSTUNAttr(&attrs, attrXorMappedAddress, xma[:])

	// 2. MAPPED-ADDRESS (0x0001) for RFC 3489 legacy compatibility
	var ma [8]byte
	ma[0] = 0x00
	ma[1] = 0x01
	binary.BigEndian.PutUint16(ma[2:4], port)
	copy(ma[4:8], ip4[:4])
	writeSTUNAttr(&attrs, attrMappedAddress, ma[:])

	// 3. RESPONSE-ORIGIN (0x802b)
	writeSTUNAttr(&attrs, attrResponseOrigin, ma[:])

	// 4. OTHER-ADDRESS (0x802c)
	writeSTUNAttr(&attrs, attrOtherAddress, ma[:])

	// 5. SOFTWARE (0x8022)
	writeSTUNAttr(&attrs, attrSoftware, []byte("NamelessSTUN"))

	bodyBytes := attrs.Bytes()

	// Build header + body + FINGERPRINT (0x8028, 8 bytes total)
	totalAttrLen := len(bodyBytes) + 8
	header := make([]byte, 20)
	binary.BigEndian.PutUint16(header[0:2], stunBindingResponse)
	binary.BigEndian.PutUint16(header[2:4], uint16(totalAttrLen))
	if isRFC5389 {
		binary.BigEndian.PutUint32(header[4:8], stunMagicCookie)
	} else {
		binary.BigEndian.PutUint32(header[4:8], stunMagicCookie)
	}
	copy(header[8:20], txID)

	preFP := make([]byte, 0, 20+len(bodyBytes)+8)
	preFP = append(preFP, header...)
	preFP = append(preFP, bodyBytes...)

	crc := crc32.ChecksumIEEE(preFP) ^ fingerprintXorMask
	var fpVal [4]byte
	binary.BigEndian.PutUint32(fpVal[:], crc)

	var fpBuf bytes.Buffer
	writeSTUNAttr(&fpBuf, attrFingerprint, fpVal[:])
	preFP = append(preFP, fpBuf.Bytes()...)
	return preFP
}

func writeSTUNAttr(buf *bytes.Buffer, attrType uint16, val []byte) {
	var hdr [4]byte
	binary.BigEndian.PutUint16(hdr[0:2], attrType)
	binary.BigEndian.PutUint16(hdr[2:4], uint16(len(val)))
	buf.Write(hdr[:])
	buf.Write(val)
	if pad := (4 - (len(val) % 4)) % 4; pad > 0 {
		buf.Write(make([]byte, pad))
	}
}

func setSpoofIP(ip net.IP, meta GeoMeta) {
	v4 := ip.To4()
	if v4 == nil || v4.IsUnspecified() || v4.IsLoopback() {
		return
	}
	mu.Lock()
	defer mu.Unlock()
	currentIP = make(net.IP, 4)
	copy(currentIP, v4)
	if meta.IP == "" {
		meta.IP = v4.String()
	}
	meta.UpdatedAt = time.Now().Unix()
	currentMeta = meta
}

func getSpoofIP() net.IP {
	mu.RLock()
	defer mu.RUnlock()
	if currentIP == nil {
		return nil
	}
	cp := make(net.IP, len(currentIP))
	copy(cp, currentIP)
	return cp
}

func getMeta() GeoMeta {
	mu.RLock()
	defer mu.RUnlock()
	return currentMeta
}

func backgroundIPRefresher() {
	// Fast initial retries right after sing-box starts
	for i := 0; i < 6; i++ {
		if getSpoofIP() != nil && getMeta().Country != "" {
			break
		}
		refreshExitIPOnce(3500 * time.Millisecond)
		time.Sleep(1200 * time.Millisecond)
	}

	ticker := time.NewTicker(25 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		refreshExitIPOnce(5 * time.Second)
	}
}

func newProxyHTTPClient(timeout time.Duration) *http.Client {
	proxyURL, err := url.Parse("http://" + localProxy)
	if err != nil {
		proxyURL, _ = url.Parse("http://127.0.0.1:10809")
	}
	tr := &http.Transport{
		Proxy:                 http.ProxyURL(proxyURL),
		DisableKeepAlives:     true,
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
	}
	return &http.Client{
		Transport: tr,
		Timeout:   timeout,
	}
}

func refreshExitIPOnce(timeout time.Duration) {
	if !refreshMu.TryLock() {
		return
	}
	defer refreshMu.Unlock()

	client := newProxyHTTPClient(timeout)

	// 1. Try ipwho.is (HTTPS over proxy port 443)
	if ip, meta, ok := fetchIPWhoIs(client); ok {
		setSpoofIP(ip, meta)
		return
	}
	// 2. Try ip-api.com (HTTP over proxy)
	if ip, meta, ok := fetchIPAPI(client); ok {
		setSpoofIP(ip, meta)
		return
	}
	// 3. Fallback plain-text IP endpoints over HTTPS/HTTP through local sing-box proxy
	plainEndpoints := []string{
		"https://api.ipify.org",
		"https://icanhazip.com",
		"http://api.ipify.org",
		"http://icanhazip.com",
	}
	for _, ep := range plainEndpoints {
		req, err := http.NewRequest("GET", ep, nil)
		if err != nil {
			continue
		}
		req.Header.Set("User-Agent", "curl/8.5.0")
		resp, err := client.Do(req)
		if err != nil {
			continue
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 128))
		_ = resp.Body.Close()
		ipStr := strings.TrimSpace(string(body))
		if parsed := net.ParseIP(ipStr); parsed != nil {
			if v4 := parsed.To4(); v4 != nil && !v4.IsUnspecified() && !v4.IsLoopback() {
				prev := getMeta()
				prev.IP = v4.String()
				setSpoofIP(v4, prev)
				return
			}
		}
	}
}

func fetchIPWhoIs(client *http.Client) (net.IP, GeoMeta, bool) {
	req, err := http.NewRequest("GET", "https://ipwho.is/", nil)
	if err != nil {
		return nil, GeoMeta{}, false
	}
	req.Header.Set("User-Agent", "curl/8.5.0")
	resp, err := client.Do(req)
	if err != nil {
		return nil, GeoMeta{}, false
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if err != nil {
		return nil, GeoMeta{}, false
	}
	var parsed struct {
		Success     bool   `json:"success"`
		IP          string `json:"ip"`
		Country     string `json:"country"`
		CountryCode string `json:"country_code"`
		City        string `json:"city"`
		Connection  struct {
			ISP string `json:"isp"`
			Org string `json:"org"`
		} `json:"connection"`
		Timezone struct {
			ID string `json:"id"`
		} `json:"timezone"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, GeoMeta{}, false
	}
	ip := net.ParseIP(strings.TrimSpace(parsed.IP))
	if ip == nil || ip.To4() == nil {
		return nil, GeoMeta{}, false
	}
	return ip.To4(), GeoMeta{
		IP:          ip.To4().String(),
		Country:     parsed.Country,
		CountryCode: parsed.CountryCode,
		City:        parsed.City,
		ISP:         parsed.Connection.ISP,
		Org:         parsed.Connection.Org,
		Timezone:    parsed.Timezone.ID,
	}, true
}

func fetchIPAPI(client *http.Client) (net.IP, GeoMeta, bool) {
	req, err := http.NewRequest("GET", "http://ip-api.com/json/?fields=status,country,countryCode,city,isp,org,timezone,query", nil)
	if err != nil {
		return nil, GeoMeta{}, false
	}
	req.Header.Set("User-Agent", "curl/8.5.0")
	resp, err := client.Do(req)
	if err != nil {
		return nil, GeoMeta{}, false
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if err != nil {
		return nil, GeoMeta{}, false
	}
	var parsed struct {
		Status      string `json:"status"`
		Country     string `json:"country"`
		CountryCode string `json:"countryCode"`
		City        string `json:"city"`
		ISP         string `json:"isp"`
		Org         string `json:"org"`
		Timezone    string `json:"timezone"`
		Query       string `json:"query"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil || parsed.Status != "success" {
		return nil, GeoMeta{}, false
	}
	ip := net.ParseIP(strings.TrimSpace(parsed.Query))
	if ip == nil || ip.To4() == nil {
		return nil, GeoMeta{}, false
	}
	return ip.To4(), GeoMeta{
		IP:          ip.To4().String(),
		Country:     parsed.Country,
		CountryCode: parsed.CountryCode,
		City:        parsed.City,
		ISP:         parsed.ISP,
		Org:         parsed.Org,
		Timezone:    parsed.Timezone,
	}, true
}

func startControlServer(addr string) {
	mux := http.NewServeMux()
	mux.HandleFunc("/update", func(w http.ResponseWriter, r *http.Request) {
		qIP := strings.TrimSpace(r.URL.Query().Get("ip"))
		if parsed := net.ParseIP(qIP); parsed != nil && parsed.To4() != nil {
			v4 := parsed.To4()
			meta := getMeta()
			meta.IP = v4.String()
			if c := r.URL.Query().Get("country"); c != "" {
				meta.Country = c
			}
			if cc := r.URL.Query().Get("cc"); cc != "" {
				meta.CountryCode = cc
			}
			if isp := r.URL.Query().Get("isp"); isp != "" {
				meta.ISP = isp
			}
			setSpoofIP(v4, meta)
			w.Header().Set("Content-Type", "application/json")
			_, _ = fmt.Fprintf(w, `{"ok":true,"ip":"%s"}`, v4.String())
			return
		}
		go refreshExitIPOnce(3 * time.Second)
		w.Header().Set("Content-Type", "application/json")
		_, _ = fmt.Fprintf(w, `{"ok":false}`)
	})

	mux.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		if getSpoofIP() == nil {
			refreshExitIPOnce(2200 * time.Millisecond)
		}
		meta := getMeta()
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(meta)
	})

	srv := &http.Server{
		Addr:         addr,
		Handler:      mux,
		ReadTimeout:  4 * time.Second,
		WriteTimeout: 4 * time.Second,
	}
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Printf("control server error: %v", err)
	}
}
