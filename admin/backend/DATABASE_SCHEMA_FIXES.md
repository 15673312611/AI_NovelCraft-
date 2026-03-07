# Database Schema Alignment Fixes

## Overview
Fixed all backend entities, mappers, and services to match the actual database schema in `database/ai_novel.sql`.

## Key Issues Fixed

### 1. User Entity & Role Handling
**Problem**: User entity had a `role` field, but the database doesn't have a `role` column in the `users` table. Roles are managed through the `user_roles` junction table.

**Solution**:
- Removed `role` field from User entity
- Added `@TableField(exist = false) List<String> roles` for transient role data
- Added all missing fields: `nickname`, `avatarUrl`, `bio`, `lastLoginAt`, `emailVerified`
- Updated UserMapper with queries that JOIN `user_roles` and `roles` tables
- Updated AdminAuthService to query roles from junction table
- Updated AdminUserService to handle role insertion/deletion through junction table

**Files Modified**:
- `admin/backend/src/main/java/com/novel/admin/entity/User.java`
- `admin/backend/src/main/java/com/novel/admin/mapper/UserMapper.java`
- `admin/backend/src/main/java/com/novel/admin/dto/UserDTO.java`
- `admin/backend/src/main/java/com/novel/admin/service/AdminUserService.java`
- `admin/backend/src/main/java/com/novel/admin/service/AdminAuthService.java`

### 2. Novel Entity
**Problem**: Novel entity used `userId` field, but database has `created_by` column.

**Solution**:
- Changed `userId` to `createdBy` to match database
- Added all missing fields from database schema:
  - `subtitle`, `coverImageUrl`, `description`, `tags`, `targetAudience`
  - `authorId`, `estimatedCompletion`, `startedAt`, `completedAt`
  - `isPublic`, `rating`, `ratingCount`
  - `targetTotalChapters`, `wordsPerChapter`, `plannedVolumeCount`, `totalWordTarget`
  - `outline`, `creationStage`

**Files Modified**:
- `admin/backend/src/main/java/com/novel/admin/entity/Novel.java`

### 3. NovelMapper
**Problem**: Used incorrect column name `user_id` instead of `created_by`.

**Solution**:
- Updated all SQL queries to use `created_by` column
- Added `chapter_count` field which exists in database

**Files Modified**:
- `admin/backend/src/main/java/com/novel/admin/mapper/NovelMapper.java`

### 4. AITask Entity & Mapper
**Status**: ✅ Already correct
- AITask entity correctly uses `userId` field
- Database `ai_tasks` table has both `user_id` AND `created_by` columns
- AITaskMapper correctly uses `user_id` in JOIN queries

### 5. Prompt Entity (Templates)
**Problem**: Missing many fields from database schema.

**Solution**:
- Added all missing fields:
  - `style`, `description`, `difficulty`, `tags`
  - `usageCount`, `effectivenessScore`, `examples`, `author`
  - `isPublic`, `createdBy`
- Updated TemplateMapper to query all relevant fields
- Updated TemplateDTO to include new fields

**Files Modified**:
- `admin/backend/src/main/java/com/novel/admin/entity/Prompt.java`
- `admin/backend/src/main/java/com/novel/admin/mapper/TemplateMapper.java`
- `admin/backend/src/main/java/com/novel/admin/dto/TemplateDTO.java`

## Database Schema Reference

### users table
- Does NOT have `role` column
- Roles stored in `user_roles` junction table
- Fields: `id`, `username`, `email`, `password`, `nickname`, `avatar_url`, `bio`, `status`, `last_login_at`, `email_verified`, `created_at`, `updated_at`

### user_roles table (junction)
- `user_id` (FK to users.id)
- `role_id` (FK to roles.id)

### roles table
- `id`, `name`, `description`, `permissions`, `created_at`

### novels table
- Uses `created_by` (not `user_id`)
- Also has `author_id` field
- Has `chapter_count` field

### ai_tasks table
- Has BOTH `user_id` AND `created_by` columns
- Queries should use `user_id` for user association

### prompts table
- Full schema with many fields for template management
- Has `created_by` field

## Compilation Status
✅ Backend compiles successfully with `mvn clean compile -DskipTests`

## Next Steps
1. Test all API endpoints with actual database
2. Verify user login with role checking works
3. Test CRUD operations for all entities
4. Add proper error handling for database constraints
