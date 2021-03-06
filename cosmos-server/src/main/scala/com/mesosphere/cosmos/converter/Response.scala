package com.mesosphere.cosmos.converter

import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.ConversionFailure
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.bijection.Conversion.asMethod

object Response {

  def v2InstallResponseToV1InstallResponse(x: rpc.v2.model.InstallResponse): rpc.v1.model.InstallResponse = {
    rpc.v1.model.InstallResponse(
      packageName = x.packageName,
      packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
      appId = x.appId
    )
  }

  def packageDefinitionToDescribeResponse(
    packageDefinition: internal.model.PackageDefinition
  ): rpc.v1.model.DescribeResponse = {
    rpc.v1.model.DescribeResponse(
      `package` = universe.v2.model.PackageDetails(
        packagingVersion = packageDefinition.packagingVersion.as[universe.v2.model.PackagingVersion],
        name = packageDefinition.name,
        version = packageDefinition.version.as[universe.v2.model.PackageDetailsVersion],
        maintainer = packageDefinition.maintainer,
        description = packageDefinition.description,
        tags = packageDefinition.tags.as[List[String]],
        selected = Some(packageDefinition.selected),
        scm = packageDefinition.scm,
        website = packageDefinition.website,
        framework = Some(packageDefinition.framework),
        preInstallNotes = packageDefinition.preInstallNotes,
        postInstallNotes = packageDefinition.postInstallNotes,
        postUninstallNotes = packageDefinition.postUninstallNotes,
        licenses = packageDefinition.licenses.as[Option[List[universe.v2.model.License]]]
      ),
      marathonMustache =
        internalPackageDefinitionMarathonToRpcV1DescribeResponseMarathon(packageDefinition.marathon),
      command = packageDefinition.command.as[Option[universe.v2.model.Command]],
      config = packageDefinition.config,
      resource = packageDefinition.resource.as[Option[universe.v2.model.Resource]]
    )
  }

  private[this] def internalPackageDefinitionMarathonToRpcV1DescribeResponseMarathon(
    v3Marathon: Option[universe.v3.model.Marathon]
  ): String = {
    v3Marathon match {
      case Some(marathon) =>
        new String(ByteBuffers.getBytes(marathon.v2AppMustacheTemplate), StandardCharsets.UTF_8)
      case _ =>
        throw ConversionFailure("Marathon template required")
    }
  }

   private[this] def getV2ResourceFromPackageDefinition(
     packageDef: universe.v3.model.PackageDefinition
   ): Option[universe.v2.model.Resource] = {
      packageDef match {
        case v2Package: universe.v3.model.V2Package =>
          v2Package.resource.as[Option[universe.v2.model.Resource]]
        case v3Package: universe.v3.model.V3Package =>
          v3Package.resource.as[Option[universe.v2.model.Resource]]
    }
  }

  def v2ListResponseToV1ListResponse(x: rpc.v2.model.ListResponse): rpc.v1.model.ListResponse = {
    rpc.v1.model.ListResponse(
      packages = x.packages.map { y =>
        rpc.v1.model.Installation(
          appId = y.appId,
          packageInformation = rpc.v1.model.InstalledPackageInformation(
              packageDefinition = y.packageInformation.as[universe.v2.model.PackageDetails],
              resourceDefinition = getV2ResourceFromPackageDefinition(y.packageInformation)
          )
        )
      }
    )
  }

}
