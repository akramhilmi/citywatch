const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();
const bucket = admin.storage().bucket();
const statsRef = db.collection("metadata").doc("stats");

/**
 * Helper function to convert report status to stat field name
 * @param {string} status - Report status (e.g., "Submitted", "Confirmed")
 * @return {string} - Stat field name (e.g., "submitted", "confirmed")
 */
function getStatFieldFromStatus(status) {
  const statusMap = {
    "Submitted": "submitted",
    "Confirmed": "confirmed",
    "In progress": "inProgress",
    "Resolved": "resolved",
  };
  return statusMap[status] || "submitted";
}

/**
 * Submit a new report to Firestore
 * Creates a new document with auto-generated ID in the 'reports' collection
 */
const submitReport = onCall(async (request) => {
  try {
    const {
      description,
      hazardType,
      localGov,
      locationDetails,
      latitude,
      longitude,
      userId,
    } = request.data;

    // Validate required fields
    if (!description ||
        !hazardType ||
        !localGov ||
        !locationDetails ||
        latitude === undefined ||
        longitude === undefined ||
        !userId) {
      throw new Error("All fields are required: description, hazardType, " +
                      "localGov, locationDetails, latitude, longitude, userId");
    }

    // Create report data
    const reportData = {
      description,
      hazardType,
      localGov,
      locationDetails,
      mapsLocation: new admin.firestore.GeoPoint(latitude, longitude),
      status: "Submitted",
      user: db.collection("users").doc(userId),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      score: 0,
      comments: 0,
    };

    // Add to Firestore and get document ID
    const docRef = await db.collection("reports").add(reportData);

    // Increment stats counter for "Submitted" status
    try {
      const statField = getStatFieldFromStatus("Submitted");
      await statsRef.update({
        [statField]: admin.firestore.FieldValue.increment(1),
      });
      logger.info(`Incremented ${statField} stat for new report ${docRef.id}`);
    } catch (statsError) {
      logger.warn(`Could not update stats for report ${docRef.id}`, statsError);
      // Don't throw - report was created successfully
    }

    logger.info(`Report created with ID: ${docRef.id} by user: ${userId}`);
    return {documentId: docRef.id, success: true};
  } catch (error) {
    logger.error("Error submitting report:", error);
    throw new Error(`Failed to submit report: ${error.message}`);
  }
});

/**
 * Upload report photo from base64 encoded string
 * Stores image in report_photos/{documentId}.jpg
 */
const uploadReportPhoto = onCall(async (request) => {
  try {
    const {documentId, imageBase64} = request.data;

    if (!documentId || !imageBase64) {
      throw new Error("Document ID and image data are required");
    }

    // Decode base64 to buffer
    const imageBuffer = Buffer.from(imageBase64, "base64");

    // Upload to Storage
    const file = bucket.file(`report_photos/${documentId}.jpg`);
    await file.save(imageBuffer, {
      metadata: {
        contentType: "image/jpeg",
      },
    });

    logger.info(`Report photo uploaded for document: ${documentId}`);
    return {success: true, message: "Photo uploaded successfully"};
  } catch (error) {
    logger.error("Error uploading report photo:", error);
    throw new Error(`Failed to upload photo: ${error.message}`);
  }
});

/**
 * Fetch all reports from Firestore with photo URLs and user names
 */
const getAllReports = onCall(async (request) => {
  try {
    const reports = [];
    const snapshot = await db.collection("reports").get();

    if (snapshot.empty) {
      logger.info("No reports found");
      return [];
    }

    for (const doc of snapshot.docs) {
      const reportData = doc.data();
      const reportId = doc.id;

      // Get user name from user reference field
      let userName = "Anonymous";
      let userId = "";
      if (reportData.user) {
        try {
          userId = reportData.user.id;
          const userDoc = await reportData.user.get();
          if (userDoc.exists && userDoc.data().name) {
            userName = userDoc.data().name;
          }
        } catch (userError) {
          logger.warn(`Could not fetch user name from reference for ${
            reportId}`, userError);
        }
      }

      // Extract latitude and longitude from GeoPoint
      let latitude = 0;
      let longitude = 0;
      if (reportData.mapsLocation) {
        latitude = reportData.mapsLocation.latitude || 0;
        longitude = reportData.mapsLocation.longitude || 0;
        logger.info(
            `Report ${reportId} location: lat=${latitude}, lon=${longitude}`);
      }

      reports.push({
        documentId: reportId,
        description: reportData.description || "",
        hazardType: reportData.hazardType || "",
        localGov: reportData.localGov || "",
        locationDetails: reportData.locationDetails || "",
        latitude,
        longitude,
        status: reportData.status || "In progress",
        userName,
        userId,
        score: reportData.score || 0,
        createdAt: reportData.createdAt ? reportData.createdAt.toMillis() : 0,
        comments: reportData.comments || 0,
      });
    }

    logger.info(`Fetched ${reports.length} reports`);
    return reports;
  } catch (error) {
    logger.error("Error fetching all reports:", error);
    throw new Error(`Failed to fetch reports: ${error.message}`);
  }
});

/**
 * Edit an existing report
 * Only the report owner (userId) can edit their report
 */
const editReport = onCall(async (request) => {
  try {
    const {
      reportId,
      userId,
      description,
      hazardType,
      localGov,
      locationDetails,
      latitude,
      longitude,
      status,
    } = request.data;

    if (!reportId || !userId) {
      throw new Error("Report ID and user ID are required");
    }

    // Get the report document
    const reportRef = db.collection("reports").doc(reportId);
    const reportDoc = await reportRef.get();

    if (!reportDoc.exists) {
      throw new Error("Report not found");
    }

    const reportData = reportDoc.data();

    // Verify ownership - check if userId matches the report's user reference
    let reportUserId = "";
    if (reportData.user) {
      reportUserId = reportData.user.id;
    }

    if (reportUserId !== userId) {
      throw new Error("Unauthorized: You can only edit your own reports");
    }

    // Prepare update data (only include fields that are provided)
    const updateData = {};

    if (description !== undefined) updateData.description = description;
    if (hazardType !== undefined) updateData.hazardType = hazardType;
    if (localGov !== undefined) updateData.localGov = localGov;
    if (locationDetails !== undefined) updateData.locationDetails = locationDetails;
    if (latitude !== undefined && longitude !== undefined) {
      updateData.mapsLocation = new admin.firestore.GeoPoint(latitude, longitude);
    }
    if (status !== undefined) updateData.status = status;
    updateData.updatedAt = admin.firestore.FieldValue.serverTimestamp();

    // Update the report
    await reportRef.update(updateData);

    logger.info(`Report ${reportId} updated by user ${userId}`);
    return {success: true, message: "Report updated successfully"};
  } catch (error) {
    logger.error("Error editing report:", error);
    throw new Error(`Failed to edit report: ${error.message}`);
  }
});

/**
 * Delete a report and its associated photo and comments
 * Only the report owner (userId) can delete their report
 */
const deleteReport = onCall(async (request) => {
  try {
    const {reportId, userId} = request.data;

    if (!reportId || !userId) {
      throw new Error("Report ID and user ID are required");
    }

    // Get the report document
    const reportRef = db.collection("reports").doc(reportId);
    const reportDoc = await reportRef.get();

    if (!reportDoc.exists) {
      throw new Error("Report not found");
    }

    const reportData = reportDoc.data();

    // Verify ownership - check if userId matches the report's user reference
    let reportUserId = "";
    if (reportData.user) {
      reportUserId = reportData.user.id;
    }

    if (reportUserId !== userId) {
      throw new Error("Unauthorized: You can only delete your own reports");
    }

    // Delete all comments associated with this report
    try {
      const commentsSnapshot = await db.collection("comments")
          .where("report", "==", reportRef)
          .get();

      const batch = db.batch();
      commentsSnapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });

      await batch.commit();
      logger.info(`Deleted ${commentsSnapshot.size} comments for report ${reportId}`);
    } catch (commentsError) {
      logger.warn(`Could not delete comments for report ${reportId}`, commentsError);
    }

    // Delete associated photo from Storage (if it exists)
    try {
      const photoFile = bucket.file(`report_photos/${reportId}.jpg`);
      await photoFile.delete();
      logger.info(`Deleted photo for report ${reportId}`);
    } catch (photoError) {
      logger.warn(`Could not delete photo for report ${reportId}`, photoError);
    }

    // Delete the comment checksum from /metadata/checksums
    try {
      const checksumRef = db.collection("metadata").doc("checksums");
      await checksumRef.update({
        [`comment_${reportId}`]: admin.firestore.FieldValue.delete(),
      });
      logger.info(`Deleted checksum for report ${reportId}`);
    } catch (checksumError) {
      logger.warn(`Could not delete checksum for report ${reportId}`, checksumError);
    }

    // Delete the report document
    await reportRef.delete();

    // Decrement stats counter based on report status
    try {
      const statField = getStatFieldFromStatus(reportData.status || "Submitted");
      await statsRef.update({
        [statField]: admin.firestore.FieldValue.increment(-1),
      });
      logger.info(`Decremented ${statField} stat for deleted report ${reportId}`);
    } catch (statsError) {
      logger.warn(`Could not update stats for deleted report ${reportId}`, statsError);
      // Don't throw - report was already deleted
    }

    logger.info(`Report ${reportId} deleted by user ${userId}`);
    return {success: true, message: "Report deleted successfully"};
  } catch (error) {
    logger.error("Error deleting report:", error);
    throw new Error(`Failed to delete report: ${error.message}`);
  }
});

module.exports = {
  submitReport,
  uploadReportPhoto,
  getAllReports,
  editReport,
  deleteReport,
};
