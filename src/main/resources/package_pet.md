@startuml
!theme plain

package "宠物管理" {
  package "controller" {
    class PetPostController
  }
  
  package "service" {
    class PetPostService
  }
  
  package "mapper" {
    class PetPostMapper
    class FavoriteRecordMapper
  }
  
  package "entity" {
    class PetPost
    class FavoriteRecord
  }
}

controller --> service
service --> mapper
mapper --> entity

@enduml
