
package jobs.generation

class Utilities {

  /**
   * Get the folder name for a job.
   *
   * @param project Project name (e.g. dotnet/coreclr)
   * @return Folder name for the project. Typically project name with / turned to _
   */
  def static getFolderName(String project) {
    return project.replace('/', '_')
  }

  /**
   * Get the project name for a job.
   *
   * @param project Project name (e.g. dotnet/coreclr)
   * @param branch Branch name (e.g. master)
   * @param targetArch Target architecture name (e.g. armel)
   * @param config Build configuration (e.g. release)
   * @return Project name for a job.
   */
  def static getProjectName(String project, String branch, String targetArch, String config) {
    return getFolderName("${project}_${branch}_${targetArch}_${config}")
  }

  /**
   * Get the docker command.
   *
   * @param projectDir (optional) Project directory to work
   * @return Docker command
   */
  def static getDockerCommand(String projectDir = "") {
    def repository = "hqueue/dotnetcore"
    def container = "ubuntu1404_cross_prereqs_v4-tizen_rootfs"
    def workingDirectory = "/opt/code"
    def environment = "-e ROOTFS_DIR=/crossrootfs/\${targetArch}.tizen.build"
    def command = "docker run --rm -v \${WORKSPACE}${projectDir}:${workingDirectory} -w=${workingDirectory} ${environment} ${repository}:${container}"
    return command
  }

  /**
   * Add a build steps.
   *
   * @param job Job to add build steps
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addBuildSteps(def job, String project, String projectDir = "") {
    if (projectDir != "") {
        projectDir = "/" + projectDir
    }

    def dockerCommand = getDockerCommand(projectDir)

    job.with {
      steps {
        if (project == 'coreclr') {
          shell("${dockerCommand} ./build.sh cross \${config} \${targetArch} cmakeargs -DFEATURE_GDBJIT=TRUE stripSymbols -OfficialBuildId=\${buildid} -- /p:Authors=Tizen")
        } else if (project == 'corefx') {
          shell("${dockerCommand} ./build.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=tizen.4.0.0 -OfficialBuildId=\${buildid} -- /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json")
          shell("${dockerCommand} ./build-packages.sh -\${config} -ArchGroup=\${targetArch} -RuntimeOS=tizen.4.0.0 -- /p:Authors=Tizen")
        } else if (project == 'core-setup') {
          shell("${dockerCommand} ./build.sh -ConfigurationGroup=\${config} -TargetArchitecture=\${targetArch} -DistroRid=tizen.4.0.0-\${targetArch} -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true -OfficialBuildId=\${buildid} -- /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json /p:Authors=Tizen")
        }
      }
    }
  }

  /**
   * Archives data for a job.
   *
   * @param job Job to modify
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addArchival(def job, String project, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    job.with {
      publishers {
        archiveArtifacts {
          if (project == 'coreclr') {
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/pkg/*.nupkg')
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/symbolpkg/*.nupkg')
          } else if (project == 'corefx') {
            pattern(projectDir + 'bin/packages/\${config}/*.nupkg')
          } else if (project == 'core-setup') {
            pattern(projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages/*.nupkg')
            pattern(projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages/*.tar.gz')
          }
          onlyIfSuccessful()
        }
      }
    }
  }

  /**
   * Get the nuget command.
   *
   * @param nugetMap Nuget configuration settings
   * @param path Directory path for uploading packages
   * @return Nuget command
   */
  def static getNugetCommand(def nugetMap, String path) {
    return "netcore-jenkins/jobs/scripts/upload_nupkg.sh netcore-jenkins/dotnet-dev " + path + " " + nugetMap['feed'] + " " + nugetMap['sfeed'] + " " + nugetMap['key']
  }

  /**
   * Upload packages for a job.
   *
   * @param job Job to modify
   * @param nugetMap Nuget configuration settings
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addUploadSteps(def job, def nugetMap, String project, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    def nugetCommand = ""
    if (project == 'coreclr') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget')
    } else if (project == 'corefx') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/packages/\${config}')
    } else if (project == 'core-setup') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'Bin/tizen.4.0.0-\${targetArch}.\${config}/packages')
    }

    job.with {
      steps {
        shell("set +x && ${nugetCommand}")
      }
    }
  }

}
