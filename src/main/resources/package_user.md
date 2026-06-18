@startuml
!theme plain

package "用户管理" {
  package "controller" {
    class UserAuthController
    class UserController
    class AvatarController
    class FollowingController
  }
  
  package "service" {
    class UserService
    class FollowService
  }
  
  package "mapper" {
    class UserMapper
    class AvatarHistoryMapper
    class FollowMapper
  }
  
  package "entity" {
    class User
    class AvatarHistory
    class Follow
  }
}

controller --> service
service --> mapper
mapper --> entity

@enduml
