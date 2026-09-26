package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/paperalt/sovereign/internal/database"
	"github.com/paperalt/sovereign/internal/middleware"
	"github.com/paperalt/sovereign/internal/model"
	"github.com/paperalt/sovereign/internal/repository"
	"github.com/paperalt/sovereign/pkg/token"
)

func TestGroupHandler_Endpoints(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	groupRepo := repository.NewGroupRepository(db)

	jwtSecret := "test-secret-key-12345678901234567890"

	user, err := userRepo.Create(context.Background(), "user1@test.com", "pass123", "User One")
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}

	jwtTok, err := token.GenerateJWT(user.ID, user.Email, jwtSecret, time.Hour)
	if err != nil {
		t.Fatalf("failed to generate token: %v", err)
	}

	groupHandler := NewGroupHandler(groupRepo, meetingRepo)
	meetingHandler := NewMeetingHandler(userRepo, meetingRepo, nil, nil, nil)
	authMW := middleware.RequireAuth(jwtSecret)

	mux := http.NewServeMux()
	mux.Handle("GET /api/v1/groups", authMW(http.HandlerFunc(groupHandler.List)))
	mux.Handle("POST /api/v1/groups", authMW(http.HandlerFunc(groupHandler.Create)))
	mux.Handle("GET /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Get)))
	mux.Handle("PUT /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Update)))
	mux.Handle("DELETE /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Delete)))
	mux.Handle("POST /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.Create)))
	mux.Handle("PUT /api/v1/meetings/{id}/group", authMW(http.HandlerFunc(groupHandler.AssignMeeting)))
	mux.Handle("DELETE /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.Delete)))

	ts := httptest.NewServer(mux)
	defer ts.Close()

	client := ts.Client()

	// 1. Create Group
	groupPayload := []byte(`{"name":"Skripsi","description":"Dokumentasi bimbingan skripsi","color":"#38BDF8"}`)
	req, _ := http.NewRequest("POST", ts.URL+"/api/v1/groups", bytes.NewReader(groupPayload))
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	req.Header.Set("Content-Type", "application/json")
	resp, err := client.Do(req)
	if err != nil || resp.StatusCode != http.StatusCreated {
		t.Fatalf("failed to create group: status=%d, err=%v", resp.StatusCode, err)
	}

	var createdGroup model.TranscriptGroup
	json.NewDecoder(resp.Body).Decode(&createdGroup)
	resp.Body.Close()

	if createdGroup.ID == "" || createdGroup.Name != "Skripsi" {
		t.Fatalf("unexpected created group: %+v", createdGroup)
	}

	// 2. Create Meeting in that Group
	mPayload := []byte(`{"title":"Bimbingan Bab 1","language":"id","group_id":"` + createdGroup.ID + `"}`)
	req, _ = http.NewRequest("POST", ts.URL+"/api/v1/meetings", bytes.NewReader(mPayload))
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	req.Header.Set("Content-Type", "application/json")
	resp, err = client.Do(req)
	if err != nil || resp.StatusCode != http.StatusCreated {
		t.Fatalf("failed to create meeting in group: status=%d, err=%v", resp.StatusCode, err)
	}

	var createdMeeting model.Meeting
	json.NewDecoder(resp.Body).Decode(&createdMeeting)
	resp.Body.Close()

	if createdMeeting.GroupID == nil || *createdMeeting.GroupID != createdGroup.ID {
		t.Fatalf("expected meeting to have group ID, got: %+v", createdMeeting.GroupID)
	}

	// 3. Get Group Details with Meetings
	req, _ = http.NewRequest("GET", ts.URL+"/api/v1/groups/"+createdGroup.ID, nil)
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	resp, err = client.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("failed to get group: status=%d, err=%v", resp.StatusCode, err)
	}

	var getGroupRes struct {
		Group    model.TranscriptGroup `json:"group"`
		Meetings []model.Meeting       `json:"meetings"`
	}
	json.NewDecoder(resp.Body).Decode(&getGroupRes)
	resp.Body.Close()

	if len(getGroupRes.Meetings) != 1 || getGroupRes.Meetings[0].Title != "Bimbingan Bab 1" {
		t.Fatalf("expected 1 meeting in group, got: %+v", getGroupRes.Meetings)
	}

	// 4. List Groups (Check MeetingCount)
	req, _ = http.NewRequest("GET", ts.URL+"/api/v1/groups", nil)
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	resp, err = client.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("failed to list groups: status=%d, err=%v", resp.StatusCode, err)
	}

	var listGroupRes struct {
		Groups []model.TranscriptGroup `json:"groups"`
	}
	json.NewDecoder(resp.Body).Decode(&listGroupRes)
	resp.Body.Close()

	if len(listGroupRes.Groups) != 1 || listGroupRes.Groups[0].MeetingCount != 1 {
		t.Fatalf("expected meeting count 1 in group, got: %+v", listGroupRes.Groups)
	}

	// 5. Delete Meeting
	req, _ = http.NewRequest("DELETE", ts.URL+"/api/v1/meetings/"+createdMeeting.ID, nil)
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	resp, err = client.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("failed to delete meeting: status=%d, err=%v", resp.StatusCode, err)
	}
	resp.Body.Close()

	// 6. Delete Group
	req, _ = http.NewRequest("DELETE", ts.URL+"/api/v1/groups/"+createdGroup.ID, nil)
	req.Header.Set("Authorization", "Bearer "+jwtTok)
	resp, err = client.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("failed to delete group: status=%d, err=%v", resp.StatusCode, err)
	}
	resp.Body.Close()
}
