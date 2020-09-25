let kubernetes = ./kubernetes.dhall

let mount =
      λ(name : Text) →
      λ(path : Text) →
      λ(subPath : Optional Text) →
        kubernetes.VolumeMount::{
        , mountPath = path
        , name
        , readOnly = Some True
        , subPath
        }

in mount