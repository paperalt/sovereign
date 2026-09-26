package repository

import (
	"context"
	"testing"

	"github.com/paperalt/sovereign/internal/database"
	"github.com/paperalt/sovereign/internal/model"
)

func TestGroupRepository_Lifecycle(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite in memory: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to run migrations: %v", err)
	}

	userRepo := NewUserRepository(db)
	meetingRepo := NewMeetingRepository(db)
	groupRepo := NewGroupRepository(db)

	ctx := context.Background()

	// 1. Create User
	user, err := userRepo.Create(ctx, "tester@eclipsegate.my.id", "secret123", "Tester Group")
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}

	// 2. Create Group
	grp, err := groupRepo.Create(ctx, &model.TranscriptGroup{
		UserID:      user.ID,
		Name:        "Kuliah Keamanan Siber",
		Description: "Rekaman mata kuliah kriptografi dan audit",
		Color:       "#38BDF8",
	})
	if err != nil {
		t.Fatalf("failed to create group: %v", err)
	}
	if grp.ID == "" || grp.Name != "Kuliah Keamanan Siber" {
		t.Fatalf("unexpected group: %+v", grp)
	}

	// 3. Create Meeting inside Group
	m1, err := meetingRepo.Create(ctx, user.ID, "Pertemuan 1 - AES 256", "id", "", &grp.ID)
	if err != nil {
		t.Fatalf("failed to create meeting in group: %v", err)
	}
	if m1.GroupID == nil || *m1.GroupID != grp.ID {
		t.Fatalf("expected meeting to have group ID %s, got: %+v", grp.ID, m1.GroupID)
	}

	// 4. Create Unassigned Meeting
	m2, err := meetingRepo.Create(ctx, user.ID, "Rapat Umum", "id", "")
	if err != nil {
		t.Fatalf("failed to create unassigned meeting: %v", err)
	}
	if m2.GroupID != nil {
		t.Fatalf("expected meeting to have nil group ID, got: %+v", m2.GroupID)
	}

	// 5. List Groups with Aggregation
	groups, err := groupRepo.ListByUser(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list groups: %v", err)
	}
	if len(groups) != 1 {
		t.Fatalf("expected 1 group, got %d", len(groups))
	}
	if groups[0].MeetingCount != 1 {
		t.Fatalf("expected meeting_count 1, got %d", groups[0].MeetingCount)
	}

	// 6. List Meetings by Group
	grpMeetings, err := meetingRepo.ListByGroup(ctx, user.ID, grp.ID)
	if err != nil {
		t.Fatalf("failed to list meetings by group: %v", err)
	}
	if len(grpMeetings) != 1 || grpMeetings[0].ID != m1.ID {
		t.Fatalf("expected meeting m1 in group, got: %+v", grpMeetings)
	}

	// 7. Assign m2 to Group
	err = meetingRepo.AssignGroup(ctx, user.ID, m2.ID, &grp.ID)
	if err != nil {
		t.Fatalf("failed to assign m2 to group: %v", err)
	}
	grpMeetingsAfter, err := meetingRepo.ListByGroup(ctx, user.ID, grp.ID)
	if err != nil || len(grpMeetingsAfter) != 2 {
		t.Fatalf("expected 2 meetings in group after assign, got %d", len(grpMeetingsAfter))
	}

	// 8. Delete Group (without deleting meetings)
	err = groupRepo.Delete(ctx, grp.ID, user.ID, false)
	if err != nil {
		t.Fatalf("failed to delete group: %v", err)
	}

	// Verify meetings still exist and group_id is null
	allMeetings, err := meetingRepo.ListByUser(ctx, user.ID, 10, 0)
	if err != nil {
		t.Fatalf("failed to list meetings: %v", err)
	}
	if len(allMeetings) != 2 {
		t.Fatalf("expected 2 meetings to remain after group deletion, got %d", len(allMeetings))
	}
	for _, m := range allMeetings {
		if m.GroupID != nil {
			t.Fatalf("expected meeting group_id to be unassigned (nil), got %v", *m.GroupID)
		}
	}
}
