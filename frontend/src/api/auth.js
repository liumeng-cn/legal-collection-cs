import http from './http'

export function login(payload) {
  return http.post('/auth/login', payload)
}

export function verifyDebtor(payload) {
  return http.post('/auth/debtor/verify', payload)
}
