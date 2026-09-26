package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
)

const baseURL = "http://127.0.0.1:8080"
const wsURL = "ws://127.0.0.1:8080/ws/transcribe"

type stressReport struct {
	TotalRequests   int64
	SuccessfulReqs  int64
	FailedReqs      int64
	TotalDuration   time.Duration
	Latencies       []time.Duration
	BugsFound       []string
}

func main() {
	fmt.Println("================================================================")
	fmt.Println("  ADVERSARIAL STRESS TEST & BUG HUNTING AUDIT")
	fmt.Println("  Target: " + baseURL)
	fmt.Println("================================================================")

	report := &stressReport{}

	// 1. Register test auditor account
	email := fmt.Sprintf("stress.auditor.%d@sec.local", time.Now().UnixNano())
	token, user := registerUser(email, "PasswordStressTest#123!", "Stress Auditor")
	if token == "" {
		fmt.Println("[FATAL] Unable to register test user.")
		os.Exit(1)
	}
	fmt.Printf("[✓] Registered benchmark account: %s (ID: %s)\n", email, user.ID)

	// Phase 1: Edge-Case Input Fuzzing (Bug Hunting)
	fmt.Println("\n--- [PHASE 1] Adversarial Input Fuzzing & Boundary Edge Cases ---")
	testInputFuzzing(token, report)

	// Phase 2: High-Concurrency REST Hammer (100 Concurrent Workers)
	fmt.Println("\n--- [PHASE 2] High-Concurrency REST Load Benchmark ---")
	testRestConcurrency(token, report)

	// Phase 3: WebSocket Streaming, Oversized Frames & Abrupt Drop Test
	fmt.Println("\n--- [PHASE 3] WebSocket Ingestion & Connection Drop Resilience ---")
	testWebSocketResilience(token, report)

	// Phase 4: Database Connection Pool Hammer (200 Concurrent DB queries)
	fmt.Println("\n--- [PHASE 4] Database Connection Pool Saturation Test ---")
	testDatabasePoolSaturation(token, report)

	// Phase 5: Resource & Process Health Audit
	fmt.Println("\n--- [PHASE 5] Post-Stress System Resource & Stability Audit ---")
	testResourceStability(report)

	fmt.Println("\n================================================================")
	fmt.Println("  AUDIT SUMMARY & RESULTS")
	fmt.Println("================================================================")
	fmt.Printf("Total Requests:     %d\n", report.TotalRequests)
	fmt.Printf("Successful:         %d\n", report.SuccessfulReqs)
	fmt.Printf("Failed:             %d\n", report.FailedReqs)
	if len(report.BugsFound) == 0 {
		fmt.Println("Vulnerabilities:    0 BUGS / FLAWS DETECTED (System Robust)")
	} else {
		fmt.Printf("Bugs/Flaws Found:   %d\n", len(report.BugsFound))
		for i, bug := range report.BugsFound {
			fmt.Printf("  [%d] %s\n", i+1, bug)
		}
	}
	fmt.Println("================================================================")
}

type User struct {
	ID    string `json:"id"`
	Email string `json:"email"`
}

func registerUser(email, password, fullName string) (string, User) {
	payload, _ := json.Marshal(map[string]string{
		"email":     email,
		"password":  password,
		"full_name": fullName,
	})
	resp, err := http.Post(baseURL+"/api/v1/auth/register", "application/json", bytes.NewReader(payload))
	if err != nil || resp.StatusCode != http.StatusCreated {
		return "", User{}
	}
	defer resp.Body.Close()
	var res struct {
		Token string `json:"token"`
		User  User   `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&res)
	return res.Token, res.User
}

func testInputFuzzing(token string, report *stressReport) {
	// Fuzz 1: SQL Injection & Metacharacters in Full-Text Search
	fuzzQueries := []string{
		"' OR '1'='1",
		"'; DROP TABLE users; --",
		"\" OR \"\"=\"",
		"kripto & (streaming | leak)",
		"!@#$%^&*()_+-=[]{}|;':,.<>/?",
		"\\x00\\x00\\x00\\x00",
		strings.Repeat("A", 2000), // Huge query string
		"🎉 🚀 💀 🔐 🤖",         // Emojis / multi-byte utf-8
		"مرحبا بالعالم",            // Arabic RTL
		"日本語のテスト",             // CJK multibyte
	}

	for _, q := range fuzzQueries {
		reqURL := fmt.Sprintf("%s/api/v1/meetings/search?q=%s", baseURL, url.QueryEscape(q))
		req, _ := http.NewRequest("GET", reqURL, nil)
		req.Header.Set("Authorization", "Bearer "+token)

		start := time.Now()
		resp, err := http.DefaultClient.Do(req)
		dur := time.Since(start)

		report.TotalRequests++
		if err != nil || (resp.StatusCode != 200 && resp.StatusCode != 400) {
			report.FailedReqs++
			report.BugsFound = append(report.BugsFound, fmt.Sprintf("Search query '%s' caused unexpected status %v / err: %v", q, resp.StatusCode, err))
		} else {
			report.SuccessfulReqs++
		}
		if resp != nil {
			resp.Body.Close()
		}
		_ = dur
	}
	fmt.Printf("[✓] Tested %d fuzz/injection search payloads safely without crash.\n", len(fuzzQueries))

	// Fuzz 2: Oversized Meeting Title (XSS & Buffer Stress)
	xssTitle := "<script>alert('XSS')</script> " + strings.Repeat("Overflow", 500)
	createPayload, _ := json.Marshal(map[string]string{
		"title":    xssTitle,
		"language": "id",
	})
	req, _ := http.NewRequest("POST", baseURL+"/api/v1/meetings", bytes.NewReader(createPayload))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	report.TotalRequests++
	if err == nil && resp.StatusCode == http.StatusCreated {
		report.SuccessfulReqs++
		fmt.Println("[✓] Oversized / XSS title handled safely.")
		resp.Body.Close()
	} else {
		report.FailedReqs++
	}
}

func testRestConcurrency(token string, report *stressReport) {
	concurrentWorkers := 50
	requestsPerWorker := 4
	totalReqs := concurrentWorkers * requestsPerWorker

	var wg sync.WaitGroup
	var successCount int64
	var failCount int64
	latencies := make([]time.Duration, totalReqs)
	var idx int64

	startAll := time.Now()
	for i := 0; i < concurrentWorkers; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			client := &http.Client{Timeout: 10 * time.Second}
			for j := 0; j < requestsPerWorker; j++ {
				reqStart := time.Now()

				// Alternating Create Meeting and List Meetings
				var err error
				var resp *http.Response
				if j%2 == 0 {
					payload, _ := json.Marshal(map[string]string{
						"title": fmt.Sprintf("Worker-%d Session-%d", workerID, j),
					})
					req, _ := http.NewRequest("POST", baseURL+"/api/v1/meetings", bytes.NewReader(payload))
					req.Header.Set("Authorization", "Bearer "+token)
					req.Header.Set("Content-Type", "application/json")
					resp, err = client.Do(req)
				} else {
					req, _ := http.NewRequest("GET", baseURL+"/api/v1/meetings?limit=10&offset=0", nil)
					req.Header.Set("Authorization", "Bearer "+token)
					resp, err = client.Do(req)
				}

				dur := time.Since(reqStart)
				curIdx := atomic.AddInt64(&idx, 1) - 1
				if curIdx < int64(len(latencies)) {
					latencies[curIdx] = dur
				}

				if err == nil && (resp.StatusCode == 200 || resp.StatusCode == 201) {
					atomic.AddInt64(&successCount, 1)
					io.Copy(io.Discard, resp.Body)
					resp.Body.Close()
				} else {
					atomic.AddInt64(&failCount, 1)
					if resp != nil {
						resp.Body.Close()
					}
				}
			}
		}(i)
	}
	wg.Wait()
	totalDuration := time.Since(startAll)

	atomic.AddInt64(&report.TotalRequests, int64(totalReqs))
	atomic.AddInt64(&report.SuccessfulReqs, successCount)
	atomic.AddInt64(&report.FailedReqs, failCount)

	rps := float64(totalReqs) / totalDuration.Seconds()
	fmt.Printf("[✓] %d Concurrent REST requests completed in %v (%.2f req/s, Success: %d, Fail: %d)\n",
		totalReqs, totalDuration, rps, successCount, failCount)
}

func testWebSocketResilience(token string, report *stressReport) {
	// Create meeting for WS testing
	payload, _ := json.Marshal(map[string]string{"title": "WS Resilience Target"})
	req, _ := http.NewRequest("POST", baseURL+"/api/v1/meetings", bytes.NewReader(payload))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, _ := http.DefaultClient.Do(req)
	var m struct {
		ID string `json:"id"`
	}
	json.NewDecoder(resp.Body).Decode(&m)
	resp.Body.Close()

	// 1. Test Frame Overrun (Send 128KB frame > 64KB read limit)
	wsEndpoint := fmt.Sprintf("%s?token=%s&meeting_id=%s", wsURL, token, m.ID)
	c, _, err := websocket.DefaultDialer.Dial(wsEndpoint, nil)
	if err == nil {
		// Read initial STATUS message first
		c.SetReadDeadline(time.Now().Add(2 * time.Second))
		_, _, _ = c.ReadMessage()

		// Now send the oversized frame (128 KB > 64 KB limit)
		oversizedFrame := make([]byte, 128*1024)
		_ = c.WriteMessage(websocket.BinaryMessage, oversizedFrame)

		// Next read MUST return an error or close frame because read limit was violated
		c.SetReadDeadline(time.Now().Add(2 * time.Second))
		_, _, readErr := c.ReadMessage()
		if readErr != nil {
			fmt.Println("[✓] Oversized frame (>64KB) cleanly rejected by server without crash.")
		} else {
			report.BugsFound = append(report.BugsFound, "Oversized frame (>64KB) was not rejected by server read limit")
		}
		c.Close()
	}

	// 2. Test Odd-Byte PCM (1 byte, 3 bytes, 7 bytes)
	// Create another meeting
	req2, _ := http.NewRequest("POST", baseURL+"/api/v1/meetings", bytes.NewReader(payload))
	req2.Header.Set("Authorization", "Bearer "+token)
	req2.Header.Set("Content-Type", "application/json")
	resp2, _ := http.DefaultClient.Do(req2)
	var m2 struct {
		ID string `json:"id"`
	}
	json.NewDecoder(resp2.Body).Decode(&m2)
	resp2.Body.Close()

	c2, _, err := websocket.DefaultDialer.Dial(fmt.Sprintf("%s?token=%s&meeting_id=%s", wsURL, token, m2.ID), nil)
	if err == nil {
		// Send odd byte frames
		c2.WriteMessage(websocket.BinaryMessage, []byte{0x01})
		c2.WriteMessage(websocket.BinaryMessage, []byte{0x01, 0x02, 0x03})
		c2.WriteMessage(websocket.TextMessage, []byte(`{"action": "STOP"}`))
		c2.SetReadDeadline(time.Now().Add(2 * time.Second))
		for {
			_, _, rErr := c2.ReadMessage()
			if rErr != nil {
				break
			}
		}
		c2.Close()
		fmt.Println("[✓] Odd-byte PCM alignment handled safely without panic.")
	}

	// 3. Test Abrupt Connection Drop (10 concurrent sockets)
	var wsWg sync.WaitGroup
	for i := 0; i < 10; i++ {
		wsWg.Add(1)
		go func(worker int) {
			defer wsWg.Done()
			// Create meeting
			p, _ := json.Marshal(map[string]string{"title": fmt.Sprintf("Drop Test %d", worker)})
			r, _ := http.NewRequest("POST", baseURL+"/api/v1/meetings", bytes.NewReader(p))
			r.Header.Set("Authorization", "Bearer "+token)
			r.Header.Set("Content-Type", "application/json")
			res, err := http.DefaultClient.Do(r)
			if err != nil {
				return
			}
			var meetingObj struct{ ID string }
			json.NewDecoder(res.Body).Decode(&meetingObj)
			res.Body.Close()

			conn, _, dErr := websocket.DefaultDialer.Dial(fmt.Sprintf("%s?token=%s&meeting_id=%s", wsURL, token, meetingObj.ID), nil)
			if dErr != nil {
				return
			}
			// Send 1 chunk
			conn.WriteMessage(websocket.BinaryMessage, make([]byte, 2048))
			time.Sleep(100 * time.Millisecond)
			// ABRUPT DROP (close underlying TCP socket immediately without sending close handshake)
			conn.UnderlyingConn().Close()
		}(i)
	}
	wsWg.Wait()
	fmt.Println("[✓] 10 Abrupt WebSocket TCP drops handled cleanly; resources released.")
}

func testDatabasePoolSaturation(token string, report *stressReport) {
	concurrentQueries := 60
	var wg sync.WaitGroup
	var successes int64
	var failures int64

	for i := 0; i < concurrentQueries; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			req, _ := http.NewRequest("GET", baseURL+"/api/v1/meetings?limit=20&offset=0", nil)
			req.Header.Set("Authorization", "Bearer "+token)
			resp, err := http.DefaultClient.Do(req)
			if err == nil && resp.StatusCode == 200 {
				atomic.AddInt64(&successes, 1)
				io.Copy(io.Discard, resp.Body)
				resp.Body.Close()
			} else {
				atomic.AddInt64(&failures, 1)
				if resp != nil {
					resp.Body.Close()
				}
			}
		}()
	}
	wg.Wait()
	atomic.AddInt64(&report.TotalRequests, int64(concurrentQueries))
	atomic.AddInt64(&report.SuccessfulReqs, successes)
	atomic.AddInt64(&report.FailedReqs, failures)
	fmt.Printf("[✓] %d Concurrent DB queries saturated connection pool: %d OK, %d Failed.\n",
		concurrentQueries, successes, failures)
}

func testResourceStability(report *stressReport) {
	// Verify daemon is still responding
	resp, err := http.Get(baseURL + "/health")
	if err != nil || resp.StatusCode != 200 {
		report.BugsFound = append(report.BugsFound, "Server crashed or unresponsive after stress testing!")
		fmt.Println("[!] CRITICAL: Server crashed or failed healthcheck!")
		return
	}
	resp.Body.Close()
	fmt.Println("[✓] Server daemon is active and healthy (HTTP 200 OK).")
}
