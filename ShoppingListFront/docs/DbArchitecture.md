## 1. Database Architecture Overview

The database consists of **5 primary tables** designed to support user authentication, role-based sharing, shopping list items, session tracking, and the 30-day soft-delete archive feature:

- **`users`**: Stores user accounts, secure passwords, system administrator flags, and recovery keys.

- **`lists`**: Stores shopping lists created by owners, including metadata for soft deletion (archive).

- **`list_shares`**: Manages granular access permissions (`editor` or `viewer`) for shared lists.

- **`items`**: Stores individual shopping list items with quantities, units, and checked status.

- **`sessions`**: Handles secure session tokens and session persistency.

## 2. Detailed Table Design

### A. `users` Table

- **`id`**: Primary Key (Auto-incrementing integer).

- **`username`**: Unique username (3–30 characters, alphanumeric as specified in AC-1).

- **`password_hash`**: Securely hashed password.

- **`recovery_key_hash`**: SHA-256 hash of the 16-character recovery key. The plaintext key is returned only during registration.

- **`is_admin`**: Boolean flag identifying System Administrators.

- **`created_at`**: Timestamp of account creation.

### B. `lists` Table

- **`id`**: Primary Key.

- **`owner_id`**: Foreign key referencing `users(id)` (List Owner with full management rights).

- **`title`**: Name of the shopping list.

- **`is_deleted`**: Boolean flag for soft deletion.

- **`deleted_at`**: Timestamp marking when the list was moved to the archive (retained for 30 days before permanent purging).

- **`created_at` & `updated_at`**: Timestamps for tracking modifications.

### C. `list_shares` Table

- **`id`**: Primary Key.

- **`list_id`**: Foreign key referencing `lists(id)`.

- **`user_id`**: Foreign key referencing `users(id)` for the recipient.

- **`permission`**: Enum/Varchar restricted to `'editor'` (Szerkesztő) or `'viewer'` (Olvasó).

- **`created_at`**: Timestamp when the share was established.

- *Constraint*: `UNIQUE (list_id, user_id)` ensures a user cannot have duplicate permission entries for the same list.

### D. `items` Table

- **`id`**: Primary Key.

- **`list_id`**: Foreign key referencing `lists(id)` (Cascades on delete).

- **`name`**: Name of the shopping list item (cannot be empty or spaces-only).

- **`quantity`**: Numeric quantity (must be a positive number).

- **`unit`**: Unit of measurement (e.g., db, kg, liter).

- **`is_checked`**: Boolean representing whether the item is checked off (`kipipálva`).

- **`created_by`**: Foreign key referencing `users(id)` who added the item.

- **`created_at` & `updated_at`**: Timestamps for real-time synchronization tracking.

### E. `sessions` Table

- **`id`**: Primary Key.

- **`user_id`**: Foreign key referencing `users(id)`.

- **`token`**: Secure session token string.

- **`expires_at`**: Token expiration timestamp.

- **`created_at`**: Timestamp of session creation.

## 3. User Authentication API

All endpoints are under `/api/users`. Registration and login issue an HS256 JWT with a 24-hour lifetime; send it on protected requests as `Authorization: Bearer <token>`. Active tokens are recorded in `sessions`, allowing logout and password resets to revoke them.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/users/register` | Create an account and return its JWT and one-time recovery key. Usernames must be 3–30 alphanumeric/underscore characters; passwords must be at least 8 characters. |
| `POST` | `/api/users/login` | Authenticate and issue a JWT. |
| `GET` | `/api/users/me` | Return the authenticated user's public profile. |
| `POST` | `/api/users/logout` | Revoke the current JWT. |
| `POST` | `/api/users/password/reset` | Reset a password using the username and recovery key; revoke all existing sessions. |

The application initializes its H2 database from `src/main/resources/scheme.sql` on startup and stores data in `./data/shoppinglist`. The schema is safely re-applied on startup; Hibernate validates the entity mappings against it. The H2 web console is available at `/h2-console`. Set `JWT_SECRET` to a stable secret of at least 32 UTF-8 bytes to keep JWTs valid across application restarts. When omitted, the application generates a temporary key at startup.

## 4. Shopping List API

All list endpoints require a Bearer JWT. Owners can create, rename, archive, restore, share, and revoke shares. Active lists shared with the authenticated user appear in `GET /api/lists`; viewers can read but cannot manage list metadata or shares. Archived lists are permanently purged hourly after 30 days.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/lists` | Create an owned list with `{"title":"..."}`. |
| `GET` | `/api/lists` | List active owned and shared lists, including each user's permission. |
| `GET` | `/api/lists/{listId}` | Read a list if owned or shared with the current user. |
| `POST` | `/api/lists/{listId}/items` | Add an item with `{"name":"Milk","quantity":1,"unit":"liter"}`; owner/editor only, with positive quantity. |
| `DELETE` | `/api/lists/{listId}/items/{itemId}` | Remove an item from an active list; owner/editor only. |
| `PATCH` | `/api/lists/{listId}` | Rename an owned list with `{"title":"..."}`. |
| `DELETE` | `/api/lists/{listId}` | Soft-delete/archive an owned list. |
| `GET` | `/api/lists/archive` | List the current user's archived lists. |
| `POST` | `/api/lists/{listId}/restore` | Restore an owned list archived within the past 30 days. |
| `GET` | `/api/lists/{listId}/shares` | List recipients and permissions for an owned list. |
| `PUT` | `/api/lists/{listId}/shares/{username}` | Share with a user or change their permission (`editor` or `viewer`). |
| `DELETE` | `/api/lists/{listId}/shares/{username}` | Revoke a recipient's access. |