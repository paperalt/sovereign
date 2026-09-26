package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/paperalt/sovereign/internal/middleware"
	"github.com/paperalt/sovereign/internal/model"
	"github.com/paperalt/sovereign/internal/repository"
)

type GroupHandler struct {
	groupRepo   repository.GroupRepository
	meetingRepo repository.MeetingRepository
}

func NewGroupHandler(groupRepo repository.GroupRepository, meetingRepo repository.MeetingRepository) *GroupHandler {
	return &GroupHandler{
		groupRepo:   groupRepo,
		meetingRepo: meetingRepo,
	}
}

// List returns all groups for the authenticated user with aggregated counts.
func (h *GroupHandler) List(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	groups, err := h.groupRepo.ListByUser(r.Context(), claims.UserID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to list groups")
		return
	}
	if groups == nil {
		groups = []model.TranscriptGroup{}
	}

	respondJSON(w, http.StatusOK, map[string]any{"groups": groups})
}

// Create creates a new transcript group.
func (h *GroupHandler) Create(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req model.CreateGroupRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Name = strings.TrimSpace(req.Name)
	if req.Name == "" {
		respondError(w, http.StatusBadRequest, "group name is required")
		return
	}

	color := strings.TrimSpace(req.Color)
	if color == "" {
		color = "#38BDF8"
	}

	group := &model.TranscriptGroup{
		UserID:      claims.UserID,
		Name:        req.Name,
		Description: strings.TrimSpace(req.Description),
		Color:       color,
	}

	created, err := h.groupRepo.Create(r.Context(), group)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to create group")
		return
	}

	respondJSON(w, http.StatusCreated, created)
}

// Get returns details of a single group along with its meetings.
func (h *GroupHandler) Get(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "missing group id")
		return
	}

	group, err := h.groupRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrGroupNotFound) {
			respondError(w, http.StatusNotFound, "group not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to fetch group")
		return
	}

	meetings, err := h.meetingRepo.ListByGroup(r.Context(), claims.UserID, id)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch group meetings")
		return
	}
	if meetings == nil {
		meetings = []model.Meeting{}
	}

	respondJSON(w, http.StatusOK, map[string]any{
		"group":    group,
		"meetings": meetings,
	})
}

// Update modifies an existing transcript group.
func (h *GroupHandler) Update(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "missing group id")
		return
	}

	var req model.UpdateGroupRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Name = strings.TrimSpace(req.Name)
	if req.Name == "" {
		respondError(w, http.StatusBadRequest, "group name is required")
		return
	}

	color := strings.TrimSpace(req.Color)
	if color == "" {
		color = "#38BDF8"
	}

	updated, err := h.groupRepo.Update(r.Context(), id, claims.UserID, req.Name, strings.TrimSpace(req.Description), color)
	if err != nil {
		if errors.Is(err, repository.ErrGroupNotFound) {
			respondError(w, http.StatusNotFound, "group not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to update group")
		return
	}

	respondJSON(w, http.StatusOK, updated)
}

// Delete removes a group, optionally deleting all meetings inside it.
func (h *GroupHandler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "missing group id")
		return
	}

	deleteMeetings := r.URL.Query().Get("delete_meetings") == "true"

	err := h.groupRepo.Delete(r.Context(), id, claims.UserID, deleteMeetings)
	if err != nil {
		if errors.Is(err, repository.ErrGroupNotFound) {
			respondError(w, http.StatusNotFound, "group not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to delete group")
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{
		"message": "group successfully deleted",
	})
}

// AssignMeeting assigns or unassigns a meeting to/from a group.
func (h *GroupHandler) AssignMeeting(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	meetingID := r.PathValue("id")
	if meetingID == "" {
		respondError(w, http.StatusBadRequest, "missing meeting id")
		return
	}

	var req model.AssignMeetingGroupRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	// If group_id is specified, verify group exists and belongs to user
	if req.GroupID != nil && *req.GroupID != "" {
		_, err := h.groupRepo.GetByID(r.Context(), *req.GroupID, claims.UserID)
		if err != nil {
			if errors.Is(err, repository.ErrGroupNotFound) {
				respondError(w, http.StatusBadRequest, "target group not found")
				return
			}
			respondError(w, http.StatusInternalServerError, "failed to verify target group")
			return
		}
	}

	err := h.meetingRepo.AssignGroup(r.Context(), claims.UserID, meetingID, req.GroupID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to assign group")
		return
	}

	// Fetch updated meeting
	updated, err := h.meetingRepo.GetByID(r.Context(), meetingID, claims.UserID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch updated meeting")
		return
	}

	respondJSON(w, http.StatusOK, updated)
}

func (h *GroupHandler) BatchAssignMeetingGroup(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req model.BatchAssignMeetingGroupRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.GroupID != nil && *req.GroupID != "" {
		_, err := h.groupRepo.GetByID(r.Context(), *req.GroupID, claims.UserID)
		if err != nil {
			if errors.Is(err, repository.ErrGroupNotFound) {
				respondError(w, http.StatusBadRequest, "target group not found")
				return
			}
			respondError(w, http.StatusInternalServerError, "failed to verify target group")
			return
		}
	}

	updatedCount := 0
	for _, mID := range req.MeetingIDs {
		mID = strings.TrimSpace(mID)
		if mID == "" {
			continue
		}
		if err := h.meetingRepo.AssignGroup(r.Context(), claims.UserID, mID, req.GroupID); err == nil {
			updatedCount++
		}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success":       true,
		"updated_count": updatedCount,
	})
}

func (h *GroupHandler) BatchDelete(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req model.BatchDeleteGroupsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	deletedCount := 0
	for _, gID := range req.GroupIDs {
		gID = strings.TrimSpace(gID)
		if gID == "" {
			continue
		}
		if err := h.groupRepo.Delete(r.Context(), gID, claims.UserID, req.DeleteMeetings); err == nil {
			deletedCount++
		}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success":       true,
		"deleted_count": deletedCount,
	})
}
