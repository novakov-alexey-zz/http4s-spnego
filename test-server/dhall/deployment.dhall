let kubernetes = ./kubernetes.dhall
let testServerTag = env:TEST_SERVER_TAG as Text ? "n/a"

let deployment =
      λ(name : Text) →
      λ(envVars : Optional (List kubernetes.EnvVar.Type)) →
      λ(volumes : List kubernetes.Volume.Type) →
      λ(mounts : List kubernetes.VolumeMount.Type) →
      λ(command : Optional (List Text)) →
        kubernetes.Deployment::{
        , metadata = kubernetes.ObjectMeta::{
          , name = Some name
          , labels = Some (toMap { app = "spnego" })
          }
        , spec = Some kubernetes.DeploymentSpec::{
          , selector = kubernetes.LabelSelector::{ matchLabels = Some (toMap { deployment = name }) }
          , replicas = Some 1
          , template = kubernetes.PodTemplateSpec::{
            , metadata = kubernetes.ObjectMeta::{ labels = Some (toMap { deployment = name }) }
            , spec = Some kubernetes.PodSpec::{
              , containers =
                [ kubernetes.Container::{
                  , name
                  , imagePullPolicy = Some "Always"
                  , image = Some "alexeyn/test-server:${testServerTag}"
                  , env = envVars
                  , volumeMounts = Some mounts
                  , ports = Some
                    [ kubernetes.ContainerPort::{ containerPort = 8080 } ]
                  , command
                  }
                ]
              , restartPolicy = Some "Always"
              , terminationGracePeriodSeconds = Some 30
              , volumes = Some volumes
              }
            }
          }
        }

in deployment        