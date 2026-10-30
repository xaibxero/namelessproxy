package main

import (
	"encoding/binary"
	"encoding/json"
	"hash/crc32"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync/atomic"
	"time"
)

const (
	adbDir       = "/data/adb/nameless_proxy"
	exitIPFile   = "/data/adb/nameless_proxy/exit_ip"
	exitJSONFile = "/data/adb/nameless_proxy/exit_info.json"
)

type GeoResponse struct {
	Status      string `json:"status"`
	Query       string `json:"query"`
	Country     string `json:"country"`
	CountryCode string `json:"countryCode"`
	RegionName  string `json:"regionName"`
	City        string `json:"city"`
	ISP         string `json:"isp"`
	Org         string `json:"org"`
}

var currentIPv4 uint32
var probeTrigger = make(chan struct{}, 1)

func parseIPv4U32(ipStr string) (uint32, bool) {
	ip := net.ParseIP(strings.TrimSpace(ipStr))
	if ip == nil {
		return 0, false
	}
	ip4 := ip.To4()
	if ip4 == nil || ip4[0] == 127 || ip4[0] == 0 {
		return 0, false
	}
	return binary.BigEndian.Uint32(ip4), true
}

func formatIPv4(u uint32) string {
	var b [4]byte
	binary.BigEndian.PutUint32(b[:], u)
	return net.IPv4(b[0], b[1], b[2], b[3]).String()
}

func setExitIP(ipStr string, geo *GeoResponse) bool {
	u, ok := parseIPv4U32(ipStr)
	if !ok {
		return false
	}
	atomic.StoreUint32(&currentIPv4, u)
	clean := formatIPv4(u)
	_ = os.WriteFile(exitIPFile, []byte(clean+"\n"), 0644)

	if geo == nil {
		geo = &GeoResponse{
			Status:      "success",
			Query:       clean,
			Country:     "Proxy Tunnel",
			CountryCode: "US",
			City:        "Protected",
			ISP:         "Nameless Root Engine",
		}
	} else {
		geo.Query = clean
	}
	if data, err := json.Marshal(geo); err == nil {
		_ = os.WriteFile(exitJSONFile, data, 0644)
	}
	return true
}

func probeTunnelExitIP(mixedPort int) {
	proxyURL, err := url.Parse("http://127.0.0.1:" + strconv.Itoa(mixedPort))
	if err != nil {
		return
	}
	client := &http.Client{
		Transport: &http.Transport{
			Proxy:               http.ProxyURL(proxyURL),
			DisableKeepAlives:   true,
			TLSHandshakeTimeout: 2000 * time.Millisecond,
		},
		Timeout: 2500 * time.Millisecond,
	}

	endpoints := []string{
		"http://ip-api.com/json/?fields=status,query,country,countryCode,regionName,city,isp,org",
		"http://checkip.amazonaws.com",
		"http://icanhazip.com",
		"https://api4.ipify.org",
		"https://ipinfo.io/ip",
	}

	// Wait briefly for tunnel.sh iptables + conntrack setup to finish
	time.Sleep(350 * time.Millisecond)

	verified := false
	for {
		for _, ep := range endpoints {
			req, err := http.NewRequest("GET", ep, nil)
			if err != nil {
				continue
			}
			req.Header.Set("User-Agent", "curl/8.5.0")
			resp, err := client.Do(req)
			if err != nil {
				continue
			}
			body, err := io.ReadAll(io.LimitReader(resp.Body, 4096))
			_ = resp.Body.Close()
			if err != nil || resp.StatusCode != 200 {
				continue
			}
			text := strings.TrimSpace(string(body))
			if strings.HasPrefix(text, "{") {
				var g GeoResponse
				if json.Unmarshal([]byte(text), &g) == nil && g.Query != "" {
					if setExitIP(g.Query, &g) {
						verified = true
						break
					}
				}
			} else {
				line := strings.Split(text, "\n")[0]
				if setExitIP(line, nil) {
					verified = true
					break
				}
			}
		}
		if verified {
			select {
			case <-probeTrigger:
			case <-time.After(20 * time.Second):
			}
		} else {
			time.Sleep(400 * time.Millisecond)
		}
	}
}

func serveSTUN(bindAddr string) {
	addr, err := net.ResolveUDPAddr("udp4", bindAddr)
	if err != nil {
		return
	}
	conn, err := net.ListenUDP("udp4", addr)
	if err != nil {
		return
	}
	defer conn.Close()
	_ = conn.SetReadBuffer(262144)
	_ = conn.SetWriteBuffer(262144)

	buf := make([]byte, 512)
	resp := make([]byte, 52)

	for {
		n, clientAddr, err := conn.ReadFromUDP(buf)
		if err != nil {
			continue
		}
		if n < 20 {
			continue
		}
		// Verify STUN Binding Request (0x0001)
		msgType := (uint16(buf[0]) << 8) | uint16(buf[1])
		if msgType != 0x0001 {
			continue
		}
		ipU32 := atomic.LoadUint32(&currentIPv4)
		if ipU32 == 0 {
			select {
			case probeTrigger <- struct{}{}:
			default:
			}
			continue
		}
		var ipBytes [4]byte
		binary.BigEndian.PutUint32(ipBytes[:], ipU32)
		clientPort := uint16(clientAddr.Port)

		// STUN Binding Success Response (0x0101), body length 32 bytes (0x0020)
		resp[0] = 0x01
		resp[1] = 0x01
		resp[2] = 0x00
		resp[3] = 0x20
		copy(resp[4:20], buf[4:20])

		// Attribute 1: XOR-MAPPED-ADDRESS (0x0020)
		resp[20] = 0x00
		resp[21] = 0x20
		resp[22] = 0x00
		resp[23] = 0x08
		resp[24] = 0x00
		resp[25] = 0x01
		resp[26] = byte(clientPort>>8) ^ buf[4]
		resp[27] = byte(clientPort&0xFF) ^ buf[5]
		resp[28] = ipBytes[0] ^ buf[4]
		resp[29] = ipBytes[1] ^ buf[5]
		resp[30] = ipBytes[2] ^ buf[6]
		resp[31] = ipBytes[3] ^ buf[7]

		// Attribute 2: MAPPED-ADDRESS (0x0001)
		resp[32] = 0x00
		resp[33] = 0x01
		resp[34] = 0x00
		resp[35] = 0x08
		resp[36] = 0x00
		resp[37] = 0x01
		resp[38] = byte(clientPort >> 8)
		resp[39] = byte(clientPort & 0xFF)
		resp[40] = ipBytes[0]
		resp[41] = ipBytes[1]
		resp[42] = ipBytes[2]
		resp[43] = ipBytes[3]

		// Attribute 3: FINGERPRINT (0x8028) per RFC 5389 Section 15.5
		fp := crc32.ChecksumIEEE(resp[:44]) ^ 0x5354554e
		resp[44] = 0x80
		resp[45] = 0x28
		resp[46] = 0x00
		resp[47] = 0x04
		binary.BigEndian.PutUint32(resp[48:52], fp)

		_, _ = conn.WriteToUDP(resp, clientAddr)
	}
}

func main() {
	_ = os.MkdirAll(adbDir, 0755)
	localPort := 10803
	hotspotPort := 10805
	mixedPort := 10801
	fallbackIP := ""

	if len(os.Args) > 1 {
		if p, err := strconv.Atoi(os.Args[1]); err == nil {
			localPort = p
		}
	}
	if len(os.Args) > 2 {
		if p, err := strconv.Atoi(os.Args[2]); err == nil {
			hotspotPort = p
		}
	}
	if len(os.Args) > 3 {
		if p, err := strconv.Atoi(os.Args[3]); err == nil {
			mixedPort = p
		}
	}
	if len(os.Args) > 4 {
		fallbackIP = os.Args[4]
	}

	if fallbackIP != "" {
		setExitIP(fallbackIP, nil)
	}

	go probeTunnelExitIP(mixedPort)
	go serveSTUN("0.0.0.0:" + strconv.Itoa(hotspotPort))
	serveSTUN("127.0.0.1:" + strconv.Itoa(localPort))
}
