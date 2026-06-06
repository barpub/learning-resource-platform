import { createStore } from 'vuex'

const token = localStorage.getItem('token') || ''
const user = JSON.parse(localStorage.getItem('user') || 'null')

export default createStore({
  state: {
    token,
    user
  },
  mutations: {
    setAuth(state, payload) {
      state.token = payload.token
      state.user = payload.user
      localStorage.setItem('token', payload.token)
      localStorage.setItem('user', JSON.stringify(payload.user))
    },
    setUser(state, user) {
      state.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    logout(state) {
      state.token = ''
      state.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
