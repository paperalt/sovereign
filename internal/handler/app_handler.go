package handler

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

type AppVersionInfo struct {
	LatestVersionCode       int64  `json:"latest_version_code"`
	LatestVersionName       string `json:"latest_version_name"`
	MinSupportedVersionCode int64  `json:"min_supported_version_code"`
	DownloadURL             string `json:"download_url"`
	ReleaseNotes            string `json:"release_notes"`
	IsCritical              bool   `json:"is_critical"`
	SHA256                  string `json:"sha256,omitempty"`
}

type AppHandler struct {
	downloadsDir string
	defaultInfo  AppVersionInfo
}

func NewAppHandler(downloadsDir string) *AppHandler {
	return &AppHandler{
		downloadsDir: downloadsDir,
		defaultInfo: AppVersionInfo{
			LatestVersionCode:       2,
			LatestVersionName:       "1.1.0",
			MinSupportedVersionCode: 1,
			DownloadURL:             "https://gate.eclipsegate.my.id/downloads/transcribe-core.apk",
			ReleaseNotes:            "Pembaruan sistem kuota audio transkripsi, integrasi paket langganan, dan peningkatan stabilitas WebSocket.",
			IsCritical:              false,
		},
	}
}

// CheckVersion checks and returns the latest available app release manifest.
func (h *AppHandler) CheckVersion(w http.ResponseWriter, r *http.Request) {
	manifestPath := filepath.Join(h.downloadsDir, "version.json")
	if data, err := os.ReadFile(manifestPath); err == nil {
		var info AppVersionInfo
		if err := json.Unmarshal(data, &info); err == nil {
			// Compute APK sha256 dynamically if empty
			if info.SHA256 == "" {
				apkPath := filepath.Join(h.downloadsDir, "transcribe-core.apk")
				if hash := computeFileSHA256(apkPath); hash != "" {
					info.SHA256 = hash
				}
			}
			respondJSON(w, http.StatusOK, info)
			return
		}
	}

	info := h.defaultInfo
	apkPath := filepath.Join(h.downloadsDir, "transcribe-core.apk")
	if hash := computeFileSHA256(apkPath); hash != "" {
		info.SHA256 = hash
	}

	respondJSON(w, http.StatusOK, info)
}

func computeFileSHA256(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()

	hasher := sha256.New()
	if _, err := io.Copy(hasher, f); err != nil {
		return ""
	}
	return hex.EncodeToString(hasher.Sum(nil))
}
