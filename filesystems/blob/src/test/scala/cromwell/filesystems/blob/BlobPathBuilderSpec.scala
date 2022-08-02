package cromwell.filesystems.blob

import com.azure.core.credential.AzureSasCredential
import com.azure.storage.common.StorageSharedKeyCredential
import com.azure.storage.common.sas.AccountSasPermission
import com.azure.storage.common.sas.AccountSasResourceType
import com.azure.storage.common.sas.AccountSasService
import com.azure.storage.common.sas.AccountSasSignatureValues
import com.azure.storage.blob.BlobServiceClientBuilder

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import java.nio.file.Files

object BlobPathBuilderSpec {
  def buildEndpoint(storageAccount: String) = s"https://$storageAccount.blob.core.windows.net"
}

class BlobPathBuilderSpec extends AnyFlatSpec with Matchers{

  it should "parse a URI into a path" in {
    val endpoint = BlobPathBuilderSpec.buildEndpoint("storageAccount")
    val container = "container"
    val evalPath = "/path/to/file"
    val testString = endpoint + "/" + container + evalPath
    BlobPathBuilder.validateBlobPath(testString, container, endpoint) match {
      case BlobPathBuilder.ValidBlobPath(path) => path should equal(evalPath)
      case BlobPathBuilder.UnparsableBlobPath(errorMessage) => fail(errorMessage)
    }
  }

  it should "bad storage account fails causes URI to fail parse into a path" in {
    val endpoint = BlobPathBuilderSpec.buildEndpoint("storageAccount")
    val container = "container"
    val evalPath = "/path/to/file"
    val testString = BlobPathBuilderSpec.buildEndpoint("badStorageAccount") + container + evalPath
    BlobPathBuilder.validateBlobPath(testString, container, endpoint) match {
      case BlobPathBuilder.ValidBlobPath(path) => fail(s"Valid path: $path found when verifying mismatched storage account")
      case BlobPathBuilder.UnparsableBlobPath(errorMessage) => errorMessage.getMessage() should equal(BlobPathBuilder.invalidBlobPathMessage(container, endpoint))
    }
  }

  it should "bad container fails causes URI to fail parse into a path" in {
    val endpoint = BlobPathBuilderSpec.buildEndpoint("storageAccount")
    val container = "container"
    val evalPath = "/path/to/file"
    val testString = endpoint + "badContainer" + evalPath
    BlobPathBuilder.validateBlobPath(testString, container, endpoint) match {
      case BlobPathBuilder.ValidBlobPath(path) => fail(s"Valid path: $path found when verifying mismatched container")
      case BlobPathBuilder.UnparsableBlobPath(errorMessage) => errorMessage.getMessage() should equal(BlobPathBuilder.invalidBlobPathMessage(container, endpoint))
    }
  }

  ignore should "build a blob path from a test string and read a file" in {
    val storageAccountName = "coaexternalstorage"
    val storageAccountKey = "<STORAGE ACCOUNT KEY GOES HERE>"
    val keyCredential = new StorageSharedKeyCredential(storageAccountName, storageAccountKey)
    val endpoint = BlobPathBuilderSpec.buildEndpoint(storageAccountName)

    val blobServiceClient = new BlobServiceClientBuilder()
      .credential(keyCredential)
      .endpoint(endpoint)
      .buildClient()
    val accountSasPermission = new AccountSasPermission()
      .setReadPermission(true)
    val services = new AccountSasService()
      .setBlobAccess(true)
      .setFileAccess(true)
      .setQueueAccess(true)
      .setTableAccess(true)
    val resourceTypes = new AccountSasResourceType()
      .setObject(true)
      .setService(true)
      .setContainer(true)
    val accountSasValues = new AccountSasSignatureValues(
      OffsetDateTime.now.plusDays(1),
      accountSasPermission,
      services,
      resourceTypes,
    )

    val store = "inputs"
    val evalPath = "/test/inputFile.txt"
    val sas = blobServiceClient.generateAccountSas(accountSasValues)
    val testString = endpoint + "/" + store + evalPath
    val blobPath: BlobPath = new BlobPathBuilder(new AzureSasCredential(sas), store, endpoint) build testString getOrElse fail()

    blobPath.container should equal(store)
    blobPath.endpoint should equal(endpoint)
    blobPath.pathAsString should equal(testString)
    blobPath.pathWithoutScheme should equal(BlobPathBuilder.parseURI(endpoint).getHost + "/" + store + evalPath)

    val is = Files.newInputStream(blobPath.nioPath)
    val fileText = (is.readAllBytes.map(_.toChar)).mkString
    fileText should include ("This is my test file!!!! Did it work?")
  }
}
