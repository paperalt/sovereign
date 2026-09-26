package config

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"
	"time"
)

type Config struct {
	BindHost          string
	Port              string
	DBDriver          string
	DBDSN             string
	JWTSecret         string
	JWTTTL            time.Duration
	NinerouterURL     string
	NinerouterKey     string
	GeminiModel       string
	GoogleClientID    string
	DownloadsDir      string
	AllowPasswordAuth bool
	AdminSecret       string
}

// LoadConfig loads environment variables with optional .env support and resilient fallbacks.
func LoadConfig() Config {
	// Attempt to load .env from common locations
	for _, envFile := range []string{".env", "/root/audio-transcribe-system/.env", "/etc/transcribe/.env", "/root/.transcribe_env"} {
		if _, err := os.Stat(envFile); err == nil {
			loadDotEnv(envFile)
			break
		}
	}

	port := getEnv("PORT", "8080")
	bindHost := getEnv("BIND_HOST", "0.0.0.0")
	dbDriver := strings.ToLower(getEnv("DB_DRIVER", "sqlite"))
	downloadsDir := getEnv("DOWNLOADS_DIR", "./downloads")

	// Ensure downloads directory exists
	if err := os.MkdirAll(downloadsDir, 0755); err != nil {
		log.Printf("[WARN] Unable to ensure downloads directory %s: %v", downloadsDir, err)
	}

	// Dynamic Database DSN construction
	dbDSN := os.Getenv("DB_DSN")
	if dbDSN == "" {
		if dbDriver == "sqlite" || dbDriver == "sqlite3" {
			sqlitePath := getEnv("SQLITE_PATH", "./data/sovereign.db")
			dbDSN = sqlitePath
		} else {
			dbHost := getEnv("DB_HOST", "127.0.0.1")
			dbPort := getEnv("DB_PORT", "5432")
			dbUser := getEnv("DB_USER", "postgres")
			dbPass := getEnv("DB_PASSWORD", "")
			dbName := getEnv("DB_NAME", "sovereign_speech")
			dbSSL := getEnv("DB_SSLMODE", "disable")

			if dbPass != "" {
				dbDSN = fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=%s", dbUser, dbPass, dbHost, dbPort, dbName, dbSSL)
			} else {
				dbDSN = fmt.Sprintf("postgres://%s@%s:%s/%s?sslmode=%s", dbUser, dbHost, dbPort, dbName, dbSSL)
			}
		}
	}

	jwtSecret := getEnv("JWT_SECRET", "sovereign-speech-default-jwt-secret-key-32b")
	geminiModel := getEnv("GEMINI_MODEL", "ag/gemini-3.8-flash")
	googleClientID := getEnv("GOOGLE_CLIENT_ID", "")

	ninerouterURL := getEnv("NINEROUTER_URL", "http://127.0.0.1:20128/v1")
	if !strings.HasSuffix(ninerouterURL, "/v1") {
		ninerouterURL = strings.TrimRight(ninerouterURL, "/") + "/v1"
	}

	ninerouterKey := getEnv("NINEROUTER_KEY", "")
	allowPasswordAuth := getEnv("ALLOW_PASSWORD_AUTH", "true") == "true"
	adminSecret := getEnv("ADMIN_SECRET", "")

	return Config{
		BindHost:          bindHost,
		Port:              port,
		DBDriver:          dbDriver,
		DBDSN:             dbDSN,
		JWTSecret:         jwtSecret,
		JWTTTL:            24 * time.Hour,
		NinerouterURL:     ninerouterURL,
		NinerouterKey:     ninerouterKey,
		GeminiModel:       geminiModel,
		GoogleClientID:    googleClientID,
		DownloadsDir:      downloadsDir,
		AllowPasswordAuth: allowPasswordAuth,
		AdminSecret:       adminSecret,
	}
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func loadDotEnv(filepath string) {
	file, err := os.Open(filepath)
	if err != nil {
		return
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			val := strings.TrimSpace(parts[1])
			// Strip quotes if present
			val = strings.Trim(val, `"'`)
			if _, exists := os.LookupEnv(key); !exists {
				os.Setenv(key, val)
			}
		}
	}
}
