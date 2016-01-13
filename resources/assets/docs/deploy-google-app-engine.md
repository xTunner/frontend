<!--

title: Continuous Deployment to Google App Engine
last_updated: July 19, 2013

-->

App Engine projects can be deployed using the gcloud command as follows:

    gcloud -q preview app deploy app.yaml --promote --version=staging

Note that while the gcloud command can be used to interact with your App Engine project, it does not include the App Engine SDK, which you may want if you are running local unit tests. It can be downloaded separately by doing the following:

```
curl -o $HOME/google_appengine_1.9.30.zip https://storage.googleapis.com/appengine-sdks/featured/google_appengine_1.9.30.zip
unzip -q -d $HOME $HOME/google_appengine_1.9.30.zip
```

## Google Managed VMs

Managed VMs projects are deployed similarly to App Engine:

    gcloud -q preview app deploy app.yaml --promote --version=staging

## Google Compute Engine And Google Container Engine

Deployment processes to Compute Engine and Container Engine can vary, but the gcloud tool is usually the foundational piece for interacting with these environments. For compute engine, you can use

    gcloud compute copy-files <artifact> <instance_name:path_to_artifact>

to copy artifacts to your instance. For Container Engine, the gcloud command can download the kubectl command

    gcloud --quiet components update kubectl
    gcloud container clusters get-credentials <your-cluster>

which can then be used to interact with your Kubernetes cluster.

# Reference Repo

A sample app that deploys a project to App Engine can be found from CircleCI and runs end-to-end tests against the environment can be found [here](https://github.com/GoogleCloudPlatform/continuous-deployment-circle). It also contains a `managed_vms` branch that shows a similar project that deploys to Managed VMs. The sample app also requires an API Key to interact with the Google Books API, which is added to the project in a similar way to the Service Account.
