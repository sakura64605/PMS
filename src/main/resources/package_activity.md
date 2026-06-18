@startuml
!theme plain

package "活动管理" {
  package "controller" {
    class ActivityController
  }
  
  package "service" {
    class ActivityService
  }
  
  package "mapper" {
    class ActivityMapper
    class ActivitySignupMapper
  }
  
  package "entity" {
    class Activity
    class ActivitySignup
  }
}

controller --> service
service --> mapper
mapper --> entity

@enduml
