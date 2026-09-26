package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/paperalt/sovereign/internal/database"
	"github.com/paperalt/sovereign/internal/middleware"
	"github.com/paperalt/sovereign/internal/model"
	"github.com/paperalt/sovereign/internal/repository"
	"github.com/paperalt/sovereign/pkg/token"
)

func TestSubscriptionAndQuota_VoucherOnly(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate db: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)
	adminSecret := "master-admin-secret-key-xyz"
	subHandler := NewSubscriptionHandler(userRepo, adminSecret)
	meetingHandler := NewMeetingHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, nil)

	jwtSecret := "test-secret-key-32-bytes-long!!"
	authMW := middleware.RequireAuth(jwtSecret)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/subscription/plans", subHandler.GetPlans)
	mux.Handle("GET /api/v1/user/quota", authMW(http.HandlerFunc(subHandler.GetUserQuota)))
	mux.Handle("POST /api/v1/subscription/topup", authMW(http.HandlerFunc(subHandler.TopUp)))
	mux.Handle("POST /api/v1/subscription/redeem", authMW(http.HandlerFunc(subHandler.RedeemVoucher)))
	mux.Handle("POST /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.Create)))

	// Admin routes
	mux.HandleFunc("POST /api/v1/admin/vouchers", subHandler.AdminCreateVoucher)
	mux.HandleFunc("GET /api/v1/admin/vouchers", subHandler.AdminListVouchers)
	mux.HandleFunc("DELETE /api/v1/admin/vouchers/{code}", subHandler.AdminDeleteVoucher)

	server := httptest.NewServer(mux)
	defer server.Close()
	client := server.Client()

	// Create test user
	ctx := context.Background()
	user, err := userRepo.CreateOAuthUser(ctx, "voucher_sub@sec.local", "Voucher User", "google_sub_2")
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}

	userToken, _ := token.GenerateJWT(user.ID, user.Email, jwtSecret, 15*time.Minute)

	// 1. Check Plans are accessible
	resp, err := client.Get(server.URL + "/api/v1/subscription/plans")
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 for plans, got %d", resp.StatusCode)
	}

	// 2. Direct /topup MUST be rejected with 403 Forbidden in voucher-only mode!
	topUpPayload := `{"plan_id": "plan_starter_60m"}`
	req, _ := http.NewRequest("POST", server.URL+"/api/v1/subscription/topup", strings.NewReader(topUpPayload))
	req.Header.Set("Authorization", "Bearer "+userToken)
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusForbidden {
		t.Fatalf("expected 403 Forbidden for unpaid topup in voucher-only mode, got %d", resp.StatusCode)
	}

	// 3. Admin: Unauthorized voucher creation attempt without X-Admin-Key MUST fail with 403
	voucherPayload := `{"code": "PRO99M", "duration_minutes": 99, "plan_name": "Paket 99 Menit", "max_uses": 5}`
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/admin/vouchers", strings.NewReader(voucherPayload))
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusForbidden {
		t.Fatalf("expected 403 Forbidden for unauthorized admin access, got %d", resp.StatusCode)
	}

	// 4. Admin: Authorized voucher creation using X-Admin-Key
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/admin/vouchers", strings.NewReader(voucherPayload))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Admin-Key", adminSecret)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201 Created for admin voucher creation, got %d", resp.StatusCode)
	}

	// 5. Admin: List vouchers
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/admin/vouchers", nil)
	req.Header.Set("X-Admin-Key", adminSecret)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 for admin list vouchers, got %d", resp.StatusCode)
	}
	var listResp struct {
		Vouchers []model.PrepaidVoucher `json:"vouchers"`
	}
	json.NewDecoder(resp.Body).Decode(&listResp)
	if len(listResp.Vouchers) == 0 {
		t.Errorf("expected at least 1 voucher in list")
	}

	// 6. User: Redeem newly created voucher PRO99M (Initial 1800s + 99*60s = 1800 + 5940 = 7740s)
	redeemPayload := `{"voucher_code": "PRO99M"}`
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/subscription/redeem", strings.NewReader(redeemPayload))
	req.Header.Set("Authorization", "Bearer "+userToken)
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 for voucher redeem, got %d", resp.StatusCode)
	}
	var redeemResult struct {
		Quota model.UserQuotaDTO `json:"quota"`
	}
	json.NewDecoder(resp.Body).Decode(&redeemResult)
	if redeemResult.Quota.RemainingSeconds != 7740 {
		t.Errorf("expected 7740s remaining after voucher PRO99M, got %d", redeemResult.Quota.RemainingSeconds)
	}

	// 7. Negative: User attempts to reuse the same voucher code -> MUST be rejected with 400 Bad Request
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/subscription/redeem", strings.NewReader(redeemPayload))
	req.Header.Set("Authorization", "Bearer "+userToken)
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400 Bad Request for duplicate voucher redemption, got %d", resp.StatusCode)
	}

	t.Log("Successfully verified voucher-only subscription model and Admin Voucher API!")
}
