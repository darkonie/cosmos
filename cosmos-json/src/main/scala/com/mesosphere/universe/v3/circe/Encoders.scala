package com.mesosphere.universe.v3.circe

import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v3.model._
import io.circe.generic.semiauto._
import io.circe.{Encoder, Json}
import io.circe.syntax._

object Encoders {

  implicit val encodeArchitectures: Encoder[Architectures] = deriveFor[Architectures].encoder
  implicit val encodeAssets: Encoder[Assets] = deriveFor[Assets].encoder
  implicit val encodeBinary: Encoder[Binary] = deriveFor[Binary].encoder
  implicit val encodeCli: Encoder[Cli] = deriveFor[Cli].encoder
  implicit val encodeCommand: Encoder[Command] = deriveFor[Command].encoder
  implicit val encodeContainer: Encoder[Container] = deriveFor[Container].encoder
  implicit val encodeDcosReleaseVersion: Encoder[DcosReleaseVersion] = Encoder.instance(_.show.asJson)
  implicit val encodeHashInfo: Encoder[HashInfo] = deriveFor[HashInfo].encoder
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }
  implicit val encodeLicense: Encoder[License] = deriveFor[License].encoder
  implicit val encodeMarathon: Encoder[Marathon] = deriveFor[Marathon].encoder
  implicit val encodePackageDefinition: Encoder[PackageDefinition] = Encoder.instance {
    case v2: V2Package => v2.asJson
    case v3: V3Package => v3.asJson
  }

  implicit val encodePlatforms: Encoder[Platforms] = deriveFor[Platforms].encoder

  implicit val encodePackageDefinitionVersion: Encoder[PackageDefinition.Version] = {
    Encoder.instance(_.toString.asJson)
  }
  implicit val encodePackageDefinitionTag: Encoder[PackageDefinition.Tag] = {
    Encoder.instance(_.value.asJson)
  }
  implicit val encodePackageDefinitionReleaseVersion: Encoder[PackageDefinition.ReleaseVersion] = {
    Encoder.instance(_.value.asJson)
  }
  implicit val encodeRepository: Encoder[Repository] = deriveFor[Repository].encoder
  implicit val encodeV2Package: Encoder[V2Package] = deriveFor[V2Package].encoder
  implicit val encodePackagingVersion: Encoder[PackagingVersion] = Encoder.instance {
    case V3PackagingVersion(v) => v.asJson
    case V2PackagingVersion(v) => v.asJson
  }
  implicit val encodeV2PackagingVersion: Encoder[V2PackagingVersion] = {
    Encoder.instance(_.v.asJson)
  }
  implicit val encodeV2Resource: Encoder[V2Resource] = deriveFor[V2Resource].encoder
  implicit val encodeV3Package: Encoder[V3Package] = deriveFor[V3Package].encoder
  implicit val encodeV3PackagingVersion: Encoder[V3PackagingVersion] = {
    Encoder.instance(_.v.asJson)
  }
  implicit val encodeV3Resource: Encoder[V3Resource] = deriveFor[V3Resource].encoder

}
