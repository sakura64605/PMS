import request from '../utils/request'

// 登录请求参数接口
interface LoginRequest {
  account: string
  password: string
}

// 登录响应数据接口
interface LoginResponse {
  code: number
  message: string
  data: {
    token: string
    tokenPrefix: string
    expiresIn: number
    userId: number
    username: string
    nickname: string
    avatar: string | null
    role: number
  }
}

// 登录接口
export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

// 退出登录接口
export const logout = (): Promise<any> => {
  return request({
    url: '/user/logout',
    method: 'post'
  })
}

// 注册请求参数接口
interface RegisterRequest {
  userName: string
  nickName?: string
  phone: string
  password: string
}

// 注册响应数据接口
interface RegisterResponse {
  code: number
  message: string
  data: {
    token: string
    tokenPrefix: string
    expiresIn: number
    userId: number
    userName: string
    nickName: string
    phone: string
    email: string | null
    avatar: string | null
    role: number
    message: string
  }
}

// 注册接口
export const register = (data: RegisterRequest): Promise<RegisterResponse> => {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

// 获取用户信息响应数据接口
interface UserInfoResponse {
  code: number
  message: string
  data: {
    userId: number
    userName: string
    password: string
    nickName: string
    avatar: string | null
    email: string | null
    phone: string | null
    gender: number
    status: number
    signature: string | null
    tags: string[]
    privacySettings: {
      tags: boolean
      email: boolean
      phone: boolean
    }
    role: number
    createTime: string
  }
}

// 获取用户信息接口
export const getUserInfo = (): Promise<UserInfoResponse> => {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

// 更新用户信息请求参数接口
interface UpdateUserRequest {
  nickName: string
  signature: string
  gender: number
  email: string
  tags: string[]
  privacySettings: {
    tags: boolean
    phone: boolean
    email: boolean
  }
  searchable: number
}

// 更新用户信息响应数据接口
interface UpdateUserResponse {
  code: number
  message: string
  data: {
    userId: number
    userName: string | null
    password: string | null
    nickName: string
    avatar: string | null
    email: string | null
    phone: string | null
    gender: number
    status: number | null
    signature: string
    tags: string[]
    privacySettings: {
      tags: boolean
      phone: boolean
      email: boolean
    }
    role: number | null
    createTime: string | null
  }
}

// 更新用户信息接口
export const updateUserInfo = (data: UpdateUserRequest): Promise<UpdateUserResponse> => {
  return request({
    url: '/user/update',
    method: 'put',
    data
  })
}

// 修改密码请求参数接口
interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// 修改密码响应数据接口
interface ChangePasswordResponse {
  code: number
  message: string
  data: string
  exception: null
}

// 修改密码接口
export const changePassword = (data: ChangePasswordRequest): Promise<ChangePasswordResponse> => {
  return request({
    url: '/user/changePassword',
    method: 'post',
    data
  })
}

// 切换到历史头像接口
export const switchToHistoryAvatar = (historyId: number): Promise<{code: number, message: string, data: string}> => {
  return request({
    url: `/avatar/switch/${historyId}`,
    method: 'put'
  })
};

// 获取用户信息（他人）
export const getUserInfoByUserId = (userId: number) => {
  return request({
    url: '/user/profileInfo',
    method: 'get',
    params: { userId }
  })
};