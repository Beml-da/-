import request from '@/utils/request';

export interface FriendVO {
  id: number;
  nickname: string;
  avatar?: string;
  school?: string;
  creditScore?: number;
  online: boolean;
}

export interface FriendRequestVO {
  id: number;
  fromUserId: number;
  fromNickname: string;
  fromAvatar?: string;
  fromSchool?: string;
  fromCreditScore?: number;
  message: string;
  createTime: string;
}

export async function getMyFriends() {
  return request<{ code: number; data: FriendVO[] }>('/api/friend/list', {
    method: 'GET',
  });
}

export async function searchUsers(keyword: string) {
  return request<{ code: number; data: FriendVO[] }>('/api/friend/search', {
    method: 'GET',
    params: { keyword },
  });
}

export async function addFriend(toUserId: number, message?: string) {
  return request<{ code: number; message?: string }>('/api/friend/add', {
    method: 'POST',
    data: { toUserId, message: message || '' },
  });
}

export async function acceptFriendRequest(requestId: number) {
  return request<{ code: number }>('/api/friend/accept', {
    method: 'POST',
    data: { requestId },
  });
}

export async function rejectFriendRequest(requestId: number) {
  return request<{ code: number }>('/api/friend/reject', {
    method: 'POST',
    data: { requestId },
  });
}

export async function deleteFriend(friendId: number) {
  return request<{ code: number }>('/api/friend/delete', {
    method: 'DELETE',
    params: { friendId },
  });
}

export async function getPendingRequests() {
  return request<{ code: number; data: FriendRequestVO[] }>('/api/friend/requests/pending', {
    method: 'GET',
  });
}

export async function getPendingRequestCount() {
  return request<{ code: number; data: number }>('/api/friend/requests/count', {
    method: 'GET',
  });
}
