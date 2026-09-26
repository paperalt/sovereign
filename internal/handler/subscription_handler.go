package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/paperalt/sovereign-speech-intelligence/internal/middleware"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
)

var AvailablePlans = []model.SubscriptionPlan{
	{
		ID:          "plan_starter_60m",
		Name:        "Paket 60 Menit",
		Description: "Tambahan 1 jam waktu transkripsi untuk rapat dan catatan perkuliahan.",
		DurationMin: 60,
		PriceIDR:    15000,
		Badge:       "STARTER",
	},
	{
		ID:          "plan_pro_300m",
		Name:        "Paket 300 Menit (5 Jam)",
		Description: "Tambahan 5 jam waktu transkripsi untuk seminar dan pembahasan panjang.",
		DurationMin: 300,
		PriceIDR:    50000,
		Badge:       "POPULER",
	},
	{
		ID:          "plan_monthly_pro",
		Name:        "Langganan Pro 1.200 Menit",
		Description: "Akses 20 jam transkripsi dengan prioritas model AI dan ringkasan eksekutif.",
		DurationMin: 1200,
		PriceIDR:    99000,
		Badge:       "PRO",
	},
}

type SubscriptionHandler struct {
	userRepo    repository.UserRepository
	adminSecret string
}

func NewSubscriptionHandler(userRepo repository.UserRepository, adminSecret string) *SubscriptionHandler {
	return &SubscriptionHandler{
		userRepo:    userRepo,
		adminSecret: adminSecret,
	}
}

// GetPlans returns the list of available subscription packages.
func (h *SubscriptionHandler) GetPlans(w http.ResponseWriter, r *http.Request) {
	respondJSON(w, http.StatusOK, map[string]interface{}{
		"plans": AvailablePlans,
	})
}

// GetUserQuota returns the real-time transcription quota balance of the authenticated user.
func (h *SubscriptionHandler) GetUserQuota(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	quota, err := h.userRepo.GetQuota(r.Context(), claims.UserID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to get quota status: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, quota)
}

// TopUp rejects arbitrary unpaid top-ups in voucher-only mode.
func (h *SubscriptionHandler) TopUp(w http.ResponseWriter, r *http.Request) {
	respondError(w, http.StatusForbidden, "pembelian langsung belum dibuka; sistem saat ini hanya menerima penambahan kuota melalui kode voucher resmi")
}

type redeemRequest struct {
	VoucherCode string `json:"voucher_code"`
}

// RedeemVoucher activates extra quota using a promotional or prepaid voucher code.
func (h *SubscriptionHandler) RedeemVoucher(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req redeemRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request payload")
		return
	}

	code := strings.ToUpper(strings.TrimSpace(req.VoucherCode))
	if code == "" {
		respondError(w, http.StatusBadRequest, "kode voucher wajib diisi")
		return
	}

	updatedQuota, msg, err := h.userRepo.RedeemVoucher(r.Context(), claims.UserID, code)
	if err != nil {
		respondError(w, http.StatusBadRequest, err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"message": msg,
		"quota":   updatedQuota,
	})
}

// -----------------------------------------------------------------------------
// Administrative Voucher Management Endpoints (Requires X-Admin-Key or role=admin)
// -----------------------------------------------------------------------------

type createVoucherRequest struct {
	Code            string `json:"code"`
	DurationMinutes int    `json:"duration_minutes"`
	PlanName        string `json:"plan_name"`
	Tier            string `json:"tier"`
	MaxUses         int    `json:"max_uses"`
}

func (h *SubscriptionHandler) AdminCreateVoucher(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(r) {
		respondError(w, http.StatusForbidden, "akses ditolak: kunci otorisasi admin tidak valid")
		return
	}

	var req createVoucherRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request payload")
		return
	}

	req.Code = strings.ToUpper(strings.TrimSpace(req.Code))
	if req.Code == "" {
		respondError(w, http.StatusBadRequest, "code voucher wajib diisi")
		return
	}
	if req.DurationMinutes <= 0 {
		respondError(w, http.StatusBadRequest, "duration_minutes harus lebih besar dari 0")
		return
	}
	if req.MaxUses <= 0 {
		req.MaxUses = 1
	}
	if req.Tier == "" {
		req.Tier = "pro"
	}
	if req.PlanName == "" {
		req.PlanName = fmt.Sprintf("Voucher Kuota %d Menit", req.DurationMinutes)
	}

	err := h.userRepo.CreateVoucher(r.Context(), req.Code, req.DurationMinutes*60, req.PlanName, req.Tier, req.MaxUses)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "gagal membuat voucher: "+err.Error())
		return
	}

	respondJSON(w, http.StatusCreated, map[string]interface{}{
		"message": "Voucher berhasil dibuat / diperbarui",
		"voucher": map[string]interface{}{
			"code":             req.Code,
			"duration_minutes": req.DurationMinutes,
			"duration_seconds": req.DurationMinutes * 60,
			"plan_name":        req.PlanName,
			"tier":             req.Tier,
			"max_uses":         req.MaxUses,
		},
	})
}

func (h *SubscriptionHandler) AdminListVouchers(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(r) {
		respondError(w, http.StatusForbidden, "akses ditolak: kunci otorisasi admin tidak valid")
		return
	}

	list, err := h.userRepo.ListVouchers(r.Context())
	if err != nil {
		respondError(w, http.StatusInternalServerError, "gagal memuat daftar voucher: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"vouchers": list,
		"total":    len(list),
	})
}

func (h *SubscriptionHandler) AdminDeleteVoucher(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(r) {
		respondError(w, http.StatusForbidden, "akses ditolak: kunci otorisasi admin tidak valid")
		return
	}

	code := strings.ToUpper(strings.TrimSpace(r.PathValue("code")))
	if code == "" {
		respondError(w, http.StatusBadRequest, "code wajib disertakan")
		return
	}

	err := h.userRepo.DeleteVoucher(r.Context(), code)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "gagal menonaktifkan voucher: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"message": fmt.Sprintf("Voucher '%s' berhasil dinonaktifkan", code),
	})
}

func (h *SubscriptionHandler) authorizeAdmin(r *http.Request) bool {
	key := r.Header.Get("X-Admin-Key")
	if key == "" {
		key = r.Header.Get("X-Admin-Secret")
	}
	if key != "" && h.adminSecret != "" && key == h.adminSecret {
		return true
	}

	claims := middleware.GetUserClaims(r.Context())
	if claims != nil {
		user, err := h.userRepo.GetByID(r.Context(), claims.UserID)
		if err == nil && user != nil && user.Role == "admin" {
			return true
		}
	}

	return false
}
