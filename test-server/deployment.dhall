let kubernetes =
      https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/master/package.dhall sha256:d541487f153cee9890ebe4145bae8899e91cd81e2f4a5b65b06dfc325fb1ae7e

let k8s =
      https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/master/typesUnion.dhall sha256:d7b8c9c574f3c894fa2bca9d9c2bec1fea972bb3acdde90e473bc2d6ee51b5b1

let testServerTag = env:TEST_SERVER_TAG as Text

let deploymentName = "testserver"
let servicePrincipal = "HTTP/${deploymentName}.test.svc.cluster.local@EXAMPLE.ORG"
let labels = Some (toMap { deployment = deploymentName })

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

let jaasConfKey = "server-jaas.conf"

let jaasConf =
      ''
      Server  {
          com.sun.security.auth.module.Krb5LoginModule required
          debug=true
          useTicketCache=true
          storeKey=true
          doNotPrompt=true
          renewTGT = true
          useKeyTab=true
          isInitiator=false
          refreshKrb5Config=true
          ticketCache="/tmp/krb"
          keyTab="/krb5/krb5.keytab"
          principal="${servicePrincipal}";
      };
      ''

let jaasConfMap =
      kubernetes.ConfigMap::{
      , metadata = kubernetes.ObjectMeta::{ name = Some "jaas-conf" }
      , data = Some [ { mapKey = jaasConfKey, mapValue = jaasConf } ]
      }

let service =
      kubernetes.Service::{
      , metadata = kubernetes.ObjectMeta::{ name = Some deploymentName }
      , spec = Some kubernetes.ServiceSpec::{
        , ports = Some
          [ kubernetes.ServicePort::{
            , name = Some "http"
            , port = 8080
            , protocol = Some "TCP"
            , targetPort = Some (kubernetes.IntOrString.Int 8080)
            }
          ]
          , selector =  labels
          , type = Some "ClusterIP"
        }
      }

let cmVolume =
      λ(name : Text) →
      λ(keyAndPath : Text) →
        kubernetes.ConfigMapVolumeSource::{
        , name = Some name
        , items = Some
          [ { key = keyAndPath, path = keyAndPath, mode = None Natural } ]
        }

let deployment =
      kubernetes.Deployment::{
      , metadata = kubernetes.ObjectMeta::{
        , name = Some deploymentName
        , labels = Some (toMap { app = "spnego" })
        }
      , spec = Some kubernetes.DeploymentSpec::{
        , selector = kubernetes.LabelSelector::{ matchLabels = labels }
        , replicas = Some 1
        , template = kubernetes.PodTemplateSpec::{
          , metadata = kubernetes.ObjectMeta::{ labels }
          , spec = Some kubernetes.PodSpec::{
            , containers =
              [ kubernetes.Container::{
                , name = deploymentName
                , imagePullPolicy = Some "Always"
                , image = Some "alexeyn/test-server:${testServerTag}"
                , ports = Some
                  [ kubernetes.ContainerPort::{ containerPort = 8080 } ]
                , volumeMounts = Some
                  [ mount "keytab" "/krb5/" (None Text)
                  , mount
                      "jaas-conf"
                      "/opt/docker/${jaasConfKey}"
                      (Some jaasConfKey)
                  ]
                , env = Some
                  [ kubernetes.EnvVar::{
                    , name = "JAAS_CONF_PATH"
                    , value = Some "/opt/docker/${jaasConfKey}"
                    },
                    kubernetes.EnvVar::{
                    , name = "PRINCIPAL"
                    , value = Some servicePrincipal
                    },
                    kubernetes.EnvVar::{
                    , name = "KEYTAB"
                    , value = Some "/krb5/krb5.keytab"
                    }
                  ]
                }
              ]
            , restartPolicy = Some "Always"
            , terminationGracePeriodSeconds = Some 30
            , volumes = Some
              [ kubernetes.Volume::{
                , name = "keytab"
                , secret = Some kubernetes.SecretVolumeSource::{
                  , secretName = Some "test-keytab"
                  }
                }
              , kubernetes.Volume::{
                , name = "jaas-conf"
                , configMap = Some (cmVolume "jaas-conf" jaasConfKey)
                }
              ]
            }
          }
        }
      }

in  { apiVersion = "v1"
    , kind = "List"
    , items = [ k8s.Deployment deployment, k8s.ConfigMap jaasConfMap, k8s.Service service ]
    }
