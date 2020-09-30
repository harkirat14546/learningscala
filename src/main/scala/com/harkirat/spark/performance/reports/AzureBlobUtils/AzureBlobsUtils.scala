//package com.harkirat.spark.performance.reports.AzureBlobUtils
//
//
//import java.io._
//import java.util
//import java.util.zip.{GZIPInputStream, GZIPOutputStream}
//
//import com.microsoft.azure.storage._
//import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem, _}
//import org.joda.time.DateTime
//
//import scala.collection.JavaConversions._
//import scala.io.Source
//import scala.util.Try
//
///**
// * Utility object to deal with Azure blob storage
// *
// * @author eforjul
// */
////TODO for write calls, replace List with Iterable when possible
//object AzureBlobsUtils {
//
//  //TODO scaladoc
//  def createContainer(connectionString: String, containerName: String): Unit = {
//    val validContainerName = containerName.matches("^[a-z0-9-]*$")
//    if(!validContainerName) {
//      throw new Exception(s">>>>Invalid container name!")
//    }
//    val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(connectionString)
//    val blobClient: CloudBlobClient = storageAccount.createCloudBlobClient
//    val container = blobClient.getContainerReference(containerName)
//    try {
//      container.createIfNotExists()
//    } catch {
//      case e: StorageException =>
//        if (e.getHttpStatusCode == 409 && e.getExtendedErrorInformation.getErrorCode.equals(StorageErrorCodeStrings.CONTAINER_BEING_DELETED)) {
//          Thread.sleep(5000)
//          createContainer(connectionString, containerName)
//        }
//        else {
//          println(">>> " + e.getExtendedErrorInformation.getErrorMessage)
//          e.printStackTrace()
//        }
//      case e: Exception =>
//        println(">>> unkbown exception")
//        e.printStackTrace()
//    }
//  }
//
//  def getContainerReference(connectionString:String, containerName:String): CloudBlobContainer = {
//    val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(connectionString)
//    val blobClient: CloudBlobClient = storageAccount.createCloudBlobClient
//    blobClient.getContainerReference(containerName)
//  }
//
//  //TODO scaladoc
//  def deleteContainer(connectionString: String, containerName: String): Unit = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    container.deleteIfExists()
//  }
//
//  //TODO scaladoc
//  def listContainers(connectionString: String): List[String] = {
//    val storageAccount: CloudStorageAccount = CloudStorageAccount.parse(connectionString)
//    val blobClient: CloudBlobClient = storageAccount.createCloudBlobClient
//    blobClient.listContainers().toList.map(container => container.getName)
//  }
//
//  /**
//   *
//   * Write a text blob to Azure blobs services
//   *
//   * @param connectionString the Azure blobs connection String
//   * @param containerName name of the container where the blob will be saved
//   * @param blobName name of the blob to be saved
//   * @param blobContent content to be saved as a List of Strings
//   */
//  def writeTextBlob(connectionString: String, containerName: String, blobName: String, blobContent: List[String]): Unit = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob: CloudBlockBlob = container.getBlockBlobReference(blobName)
//    blockBlob.uploadText(blobContent.mkString("\n"))
//
//    println(s"==== writeTextBlob $blobName at $containerName")
//  }
//
//  def appendTextBlob(connectionString: String, containerName: String, blobName: String, blobContent: List[String]): Unit = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob: CloudAppendBlob = container.getAppendBlobReference(blobName)
//    if (!blockBlob.exists()) blockBlob.createOrReplace()
//    blockBlob.appendText(blobContent.mkString("\n"))
//
//    println(s"==== appendTextBlob $blobName at $containerName")
//  }
//
//  /**
//   *
//   * delete a blob with snapshotFlog on or off
//   * @param connectionString   the Azure blobs connection String
//   * @param containerName      name of the container where the blob will be deleted
//   * @param blobName           name of the blob to be deleted
//   * @param snapshotFlag       if the snapshot will be deleted or not
//   */
//  def deleteBlob(connectionString: String, containerName: String, blobName: String, snapshotFlag: Boolean = false): Unit = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob = container.getBlockBlobReference(blobName)
//    val isSuccess = if (snapshotFlag) {
//      blockBlob.deleteIfExists(DeleteSnapshotsOption.INCLUDE_SNAPSHOTS, AccessCondition.generateEmptyCondition(),
//        new BlobRequestOptions, new OperationContext)
//    } else {
//      blockBlob.deleteIfExists()
//    }
//    println(s"==== file deleteBlob $blobName $isSuccess at $containerName")
//  }
//
//
//  /**
//   * Write a gzip compressed text blob to Azure blobs services
//   *
//   * @param connectionString the Azure blobs connection String
//   * @param containerName name of the container where the blob will be saved
//   * @param blobName name of the blob to be saved
//   * @param blobContent content to be saved as a List of Strings
//   */
//  def writeGzippedTextBlob(connectionString: String, containerName: String, blobName: String, blobContent: List[String]): Unit = {
//    val bytes = blobContent.mkString("\n").getBytes()
//    val outputStream = new ByteArrayOutputStream(bytes.length)
//    val gZIPOutputStream = new GZIPOutputStream(outputStream)
//    gZIPOutputStream.write(bytes, 0, bytes.length)
//    gZIPOutputStream.finish()
//    gZIPOutputStream.flush()
//    outputStream.flush()
//    writeBinaryBlob(connectionString, containerName, blobName, outputStream.toByteArray)
//  }
//
//  /**
//   *
//   * write an empty file (gxipped or text file) (with header or not) to blob
//   * @param connectionString
//   * @param containerName
//   * @param blobName
//   * @param headerOption Option[String]        header of the bolb
//   * @param compression  "gzip", "none"
//   */
//  def writeEmptyFileToBlob(connectionString: String, containerName: String, compression: String, blobName: String, headerOption: Option[String]): Unit = {
//    // create container if not exist
//    createContainer(connectionString, containerName)
//    val writeString = headerOption match {
//      case None => "\n"
//      case Some(header) => header
//    }
//    val writeBuffer = List(writeString)
//    compression.toLowerCase match {
//      case "gzip" => AzureBlobsUtils.writeGzippedTextBlob(connectionString, containerName, blobName + ".gz", writeBuffer)
//      case "none" => AzureBlobsUtils.writeTextBlob(connectionString, containerName, blobName, writeBuffer)
//    }
//  }
//
//  /**
//   * Write a binary blob to Azure blobs services
//   *
//   * @param connectionString the Azure blobs connection String
//   * @param containerName name of the container where the blob will be saved
//   * @param blobName name of the blob to be saved
//   * @param blobContent content to be saved as an array of Bytes
//   */
//  def writeBinaryBlob(connectionString: String, containerName: String, blobName: String, blobContent: Array[Byte]): Unit = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob: CloudBlockBlob = container.getBlockBlobReference(blobName)
//    blockBlob.uploadFromByteArray(blobContent, 0, blobContent.length)
//  }
//
//  /**
//   * Get the blob file name of the most recently modified Azure blob file in a given container
//   * @param connectionString the connection string to be used to connect to the Azure blob service
//   * @param containerName the name of the Azure blob container
//   * @return a tuple with the name of the most recently modified Azure blob file and its last modified timestamp
//   */
//  def getLatestFileFromContainer(connectionString: String, containerName: String, filter: String = ""): Option[(String, DateTime)] = {
//    getNamesByLastModifiedFromContainer(connectionString, containerName).find(_._1.contains(filter))
//  }
//
//  /**
//   * Get blob file names that have been modified since a given timestamp in a given Azure blob container
//   * @param connectionString the connection string to be used to connect to the Azure blob service
//   * @param containerName the name of the Azure blob container
//   * @param since last modified timestamp
//   * @return list of tuples with the names of the Azure blob files that have been modified since specified timestamp and their corresponding last modified
//   *         timestamps
//   */
//  def getFilesFromContainerSince(connectionString: String, containerName: String, since: DateTime): List[(String, DateTime)] = {
//    getNamesByLastModifiedFromContainer(connectionString, containerName).filter(pair => pair._2.isAfter(since))
//  }
//
//  /**
//   * Load an Azure blob text file as a List of String
//   * @param connectionString the connection string to be used to connect to the Azure blob service
//   * @param containerName the name of the Azure blob container
//   * @param blobName the name of the text blob to be loaded
//   * @return a list of String mapping the lines of the text file
//   */
//  //TODO this forces to load a full file in memory, could be improved by using block entries
//  def loadTextFile(connectionString: String, containerName: String, blobName: String): List[String] = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob: CloudBlockBlob = container.getBlockBlobReference(blobName)
//    blockBlob.downloadText.split("\\r?\\n").toList
//  }
//
//  //TODO scala doc
//  //TODO schedule??
//  def loadGzippedTextFile(connectionString: String, containerName: String, blobName: String): List[String] = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    val blockBlob: CloudBlockBlob = container.getBlockBlobReference(blobName)
//    val fileName = s"schedule/${java.util.UUID.randomUUID.toString}"
//    val tempFile = File.createTempFile(fileName, ".gz")
//    blockBlob.downloadToFile(tempFile.getAbsolutePath)
//    val content = Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(tempFile.getAbsolutePath))))
//    tempFile.delete
//    Try(List(content.mkString)).getOrElse({
//      println(s"WARNING: could not read $containerName.$blobName")
//      List[String]()
//    }
//    )
//
//  }
//
//  /**
//   * Get blob file names and their corresponding last modified timestamps in a given Azure blob container
//   * @param connectionString the connection string to be used to connect to the Azure blob service
//   * @param containerName the name of the Azure blob container
//   * @return list of tuples containing the name file and the last modified timestamp of each of the CloudBlob found in the specified container
//   */
//  private def getNamesByLastModifiedFromContainer(connectionString: String, containerName: String): List[(String, DateTime)] = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    if (!container.exists()) return List()
//    val blobs = container.listBlobs
//    if (blobs.isEmpty) return List()
//    blobs.flatMap(b => {
//      b match {
//        case cb: CloudBlob => List((cb.getName, new DateTime(cb.getProperties.getLastModified)))
//        case _             => List()
//      }
//    }).toList.sortWith((x,y) => x._2.isAfter(y._2))
//  }
//
//  def listBlobsFromContainer(connectionString: String, containerName: String, useFlatBlobListing: Boolean = false): Set[String] = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    if (!container.exists()) return Set()
//    val blobs = container.listBlobs("", useFlatBlobListing)
//    if (blobs.isEmpty) return Set()
//    blobs.flatMap {
//      case cloudBlob: CloudBlob => List(cloudBlob.getName)
//      case _                    => List()
//    }.toSet
//  }
//
//  /**
//   * recursively lookups and returns the list of blobs in the given directory and its subdirectories.
//   * If the directoryName is missing, it starts searching from the container.
//   * @param connectionString the connection string to be used to connect to the Azure blob service
//   * @param containerName the name of the Azure blob container
//   * @param directoryName if this argument is missing it starts searching the list of blobs from container
//   * @return list of blob names
//   */
//  def listBlobsOfAllSubDirectories(connectionString:String,containerName:String,directoryName:String="") :List[String] ={
//
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    container.listBlobs(directoryName).toList.map { blob =>
//      blob match {
//        case cb: CloudBlob => List(cb.getName)
//        case cd: CloudBlobDirectory => listBlobsOfAllSubDirectories(connectionString,containerName,cd.getUri.getPath.replace(container.getUri.getPath + "/", ""))
//        case _ => List[String]()
//      }
//    }.flatMap(list=>list)
//  }
//
//  /**
//   *
//   * Delete all blobs with same prefix String except top N blobs with snapshotFlag on or off in a container
//   * @param connectionString
//   * @param containerName
//   * @param cacheName
//   * @param slot
//   * @param maxBlobNumInContainer
//   * @param snapshotFlag
//   */
//  def deleteRedundantBlobs(connectionString: String, containerName: String, cacheName: String, slot: String = "",
//                           maxBlobNumInContainer: Int = 10, snapshotFlag: Boolean = false): Unit = {
//
//    //todo change api
//    val blobPrefix = cacheName + "." + slot
//    val sortedBlobNames = listBlockBlobsSorted(connectionString, containerName, blobPrefix, snapshotFlag).map(_._1)
//    println(s"====== Size of sortedBlobNames = ${sortedBlobNames.size}")
//    val redundantBlobNames = getBottomStrings(sortedBlobNames, maxBlobNumInContainer)
//    println(s"====== Size of redundantBlobNames = ${redundantBlobNames.size}")
//    if (!redundantBlobNames.isEmpty) {
//      redundantBlobNames.par.foreach(blobName => { deleteBlob(connectionString, containerName, blobName, snapshotFlag)})
//    }
//  }
//
//  /**
//   *
//   *
//   * @param connectionString
//   * @param containerName
//   * @param blobPrefix
//   * @param snapshotFlag
//   * @return     List[(fileName, time)]
//   */
//  def listBlockBlobsSorted(connectionString: String, containerName: String, blobPrefix: String = "", snapshotFlag: Boolean = false): List[(String, String)] = {
//
//    val uris = listBlobItems(connectionString, containerName, blobPrefix, snapshotFlag)
//      .map(blob => blob.getUri.toString)
//
//    println(s"blobsItems size = ${uris.size}")
//    sortUri(uris, containerName).map(uri => (uri._1, uri._4))
//  }
//
//  /**
//   *
//   * list blobs in a contianer with prefix and snapshot flag on or off
//   * @param connectionString
//   * @param containerName
//   * @param blobPrefix
//   * @param snapshotFlag
//   * @return
//   */
//  def listBlobItems(connectionString: String, containerName: String, blobPrefix: String = "", snapshotFlag: Boolean = false): List[ListBlobItem] = {
//    val container: CloudBlobContainer = getContainerReference(connectionString, containerName)
//    if (!container.exists()) return List()
//
//    //list blobs with snapshotFlag on or off
//    if (snapshotFlag) {
//      val listingDetails = util.EnumSet.of(BlobListingDetails.SNAPSHOTS);
//      //do not change true to false for useFlatBlobListing, otherwise might get Exception
//      container.listBlobs(blobPrefix, true, listingDetails, null, null).toList
//    } else {
//      container.listBlobs.filter(blob => blob.getUri.toString.contains(blobPrefix)).toList
//    }
//  }
//
//  /**
//   *
//   * get bottom Strings from a sorted Strings and remove the topN Strings
//   *
//   * @param sortedStrings
//   * @param topN  max number of files we like to keep
//   * @return
//   */
//  def getBottomStrings(sortedStrings :List[String], topN: Int): List[String] = {
//
//    if (sortedStrings.isEmpty || sortedStrings.size <= topN) {
//      List.empty[String]
//    } else {
//      sortedStrings.slice(topN, sortedStrings.size)
//    }
//  }
//
//
//  /**
//   *
//   *  delete the Redundant blobs in a container,
//   * keep maxBlobNumInContainer Blobs for the files with same prefix,
//   * @param connectionString
//   * @param containerName
//   * @param maxRemainBlobNum   max num of blobs we can keep in container
//   * @param expiredTime
//   */
//  def initialCleanupContainer(connectionString: String, containerName: String, maxRemainBlobNum: Int, expiredTime: String = "20160516025757478"): Unit = {
//
//    val blobNames: List[(String, String)] = listBlockBlobsSorted(connectionString, containerName, null, true)
//    blobNames.foreach{case (blobName:String, time:String) => if (time.compareTo(expiredTime) < 0) deleteBlob(connectionString, containerName, blobName, true)}
//
//    println(s"===== blobNames: ${blobNames.size}=============")
//    val redundantBlobMap = getRedundantBlobNames(blobNames.map(_._1), maxRemainBlobNum)
//    redundantBlobMap.map {
//      case (_, redundantBlobNames) => {
//        println(s"===== size of redundantBlobNames - ${redundantBlobNames.size} ======")
//        redundantBlobNames.par.foreach(blobName => deleteBlob(connectionString, containerName, blobName, true))
//      }
//    }
//  }
//
//  /**
//   *
//   *
//   * @param blobNames          a Map of the prefix => sorted fileNames
//   * @param maxRemainBlobNum   the max blobs we like to keep
//   * @return                   the blobs we will deleted in a Map(prefix, ListOf blobName)
//   */
//  def getRedundantBlobNames(blobNames: List[String], maxRemainBlobNum: Int): Map[String,List[String]] = {
//    if (blobNames.isEmpty) {
//      return Map.empty
//    }
//
//    // get blobsMap (key is prefix, and value is fileName)
//    val blobTuples = blobNames.map(fileName => (Try{getStringPrefixWithSlot(fileName)}.getOrElse(fileName), fileName))
//    val blobsMap = blobTuples.groupBy(_._1).mapValues(_.map(_._2))
//
//    blobsMap.map {
//      case (prefix, fileNames) => {
//        (prefix, getBottomStrings(fileNames, maxRemainBlobNum))
//      }
//    }
//  }
//
//  /**
//   *
//   * get a input string prefix with slot
//   * @param input   a file name. ex: "analytics-most-watched.s72.20170426191816239.cache"
//   * @return        the prefix with slot ex: "analytics-most-watched.s72"
//   */
//  def getStringPrefixWithSlot(input: String): String = {
//    val reg = """([A-Za-z0-9-]+[\.][s][0-9]+)([\.][A-Za-z0-9-.]+)""".r
//    val reg(prefixSlot, _) = input
//    prefixSlot
//  }
//
//  /**
//   *
//   *
//   * @param bolbUrl ex: "https://discsactlsusw.blob.core.windows.net/cache-manifest/subscriberlivetrending.s60.20170419042220707.cache"
//   * @return ex: fileName, prefix, slot, time
//   */
//  def parseUrl(bolbUrl: String, containerName: String): (String, String, String, String) = {
//
//    //val urlReg = """(https://[A-Za-z0-9.]+)(/cache-manifest/)([A-Za-z0-9.]+)""".r
//    val urlReg = s"""(https://[A-Za-z0-9-.]+)(/${containerName}/)([A-Za-z0-9-.]+)""".r
//    val urlReg(_, _, fileName) = bolbUrl
//    Try {
//      val fileNameReg = """([A-Za-z0-9-]+)[\.]([s][0-9]+)[\.]([0-9]+)[\.]cache""".r
//      val fileNameReg(prefix, slot, time) = fileName
//      (fileName, prefix, slot, time)
//    }.getOrElse(fileName, "", "","")
//
//  }
//
//  /**
//   *
//   * @param uris  List of pair (fileName, prefix, slot, time)
//   * @return
//   */
//  def sortUri(uris: List[String], containerName: String):List[(String, String, String, String)]  = {
//
//    //sort those blobs by lastModified
//    if (uris.isEmpty) return List()
//
//    uris.map( uri => parseUrl(uri, containerName) )
//      .sortBy{ case (fileName: String, prefix: String, slot: String, time: String) => (prefix, slot, time) }
//      .reverse
//  }
//
//}
//
//
