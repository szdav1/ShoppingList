import { apiRequest } from './auth'

export type ListPermission = 'OWNER' | 'EDITOR' | 'VIEWER'
export type SharePermission = 'editor' | 'viewer'

export interface ShoppingList {
  id: number
  title: string
  ownerUsername: string
  permission: ListPermission
  deleted: boolean
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface ShoppingItem {
  id: number
  listId: number
  name: string
  quantity: number
  unit: string
  checked: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface ListShare {
  username: string
  permission: SharePermission
  createdAt: string
}

function withToken(token: string, init: RequestInit = {}): RequestInit {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

function authorizedRequest<T>(token: string, path: string, init?: RequestInit): Promise<T> {
  return apiRequest<T>(path, withToken(token, init))
}

export function getActiveLists(token: string): Promise<ShoppingList[]> {
  return authorizedRequest(token, '/api/lists')
}

export function getArchivedLists(token: string): Promise<ShoppingList[]> {
  return authorizedRequest(token, '/api/lists/archive')
}

export function getList(token: string, listId: number): Promise<ShoppingList> {
  return authorizedRequest(token, `/api/lists/${listId}`)
}

export function createList(token: string, title: string): Promise<ShoppingList> {
  return authorizedRequest(token, '/api/lists', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
}

export function renameList(token: string, listId: number, title: string): Promise<ShoppingList> {
  return authorizedRequest(token, `/api/lists/${listId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
}

export function archiveList(token: string, listId: number): Promise<void> {
  return authorizedRequest(token, `/api/lists/${listId}`, { method: 'DELETE' })
}

export function restoreList(token: string, listId: number): Promise<ShoppingList> {
  return authorizedRequest(token, `/api/lists/${listId}/restore`, { method: 'POST' })
}

export function addItem(
  token: string,
  listId: number,
  item: Pick<ShoppingItem, 'name' | 'quantity' | 'unit'>,
): Promise<ShoppingItem> {
  return authorizedRequest(token, `/api/lists/${listId}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(item),
  })
}

export function removeItem(token: string, listId: number, itemId: number): Promise<void> {
  return authorizedRequest(token, `/api/lists/${listId}/items/${itemId}`, { method: 'DELETE' })
}

export function getShares(token: string, listId: number): Promise<ListShare[]> {
  return authorizedRequest(token, `/api/lists/${listId}/shares`)
}

export function setShare(
  token: string,
  listId: number,
  username: string,
  permission: SharePermission,
): Promise<ListShare> {
  return authorizedRequest(token, `/api/lists/${listId}/shares/${encodeURIComponent(username)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ permission }),
  })
}

export function revokeShare(token: string, listId: number, username: string): Promise<void> {
  return authorizedRequest(token, `/api/lists/${listId}/shares/${encodeURIComponent(username)}`, {
    method: 'DELETE',
  })
}