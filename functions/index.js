const {setGlobalOptions} = require("firebase-functions");
const {onCall} = require("firebase-functions/v2/https");
const {onDocumentDeleted} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

// Initialize Firebase Admin SDK
admin.initializeApp();

setGlobalOptions({
  maxInstances: 10,
});

const db = admin.firestore();
const auth = admin.auth();
const bucket = admin.storage().bucket();

/**
 * Validate required fields in request data
 * @param {Object} data - The data object to validate
 * @param {Array<string>} requiredFields - Array of required field names
 * @throws {Error} If any required field is missing
 * @return {void}
 */
 function validateRequiredFields(data, requiredFields) {
  for (const field of requiredFields) {
    if (!data[field] && data[field] !== 0) {
      throw new Error(`Field '${field}' is required`);
    }
  }
 }

/**
 * Retrieve user name from Firestore
 * @param {Object} request - The request object
 * @return {Promise<string|null>} User name or null if not found
 */
exports.getUserName = onCall(async (request) => {
  try {
    const {userId} = request.data;
    if (!userId) {
      throw new Error("User ID is required");
    }

    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) {
      return null;
    }

    return userDoc.data().name || null;
  } catch (error) {
    logger.error("Error fetching user name:", error);
    throw new Error(`Failed to fetch user name: ${error.message}`);
  }
});

/**
 * Retrieve user phone from Firestore
 * @param {Object} request - The request object
 * @return {Promise<string|null>} User phone or null if not found
 */
exports.getUserPhone = onCall(async (request) => {
  try {
    const {userId} = request.data;
    if (!userId) {
      throw new Error("User ID is required");
    }

    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) {
      return null;
    }

    return userDoc.data().phone || null;
  } catch (error) {
    logger.error("Error fetching user phone:", error);
    throw new Error(`Failed to fetch user phone: ${error.message}`);
  }
});

/**
 * Update user name in both Auth profile and Firestore
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success message
 */
exports.updateUserName = onCall(async (request) => {
  try {
    const {userId, newName} = request.data;
    if (!userId || !newName) {
      throw new Error("User ID and new name are required");
    }

    // Update Firebase Auth profile
    await auth.updateUser(userId, {
      displayName: newName,
    });

    // Update Firestore
    await db.collection("users").doc(userId).update({
      name: newName,
    });

    return {success: true, message: "Name updated successfully"};
  } catch (error) {
    logger.error("Error updating user name:", error);
    throw new Error(`Failed to update user name: ${error.message}`);
  }
});

/**
 * Update user phone number in Firestore
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success message
 */
exports.updateUserPhone = onCall(async (request) => {
  try {
    const {userId, newPhone} = request.data;
    if (!userId || !newPhone) {
      throw new Error("User ID and new phone are required");
    }

    await db.collection("users").doc(userId).update({
      phone: newPhone,
    });

    return {success: true, message: "Phone updated successfully"};
  } catch (error) {
    logger.error("Error updating user phone:", error);
    throw new Error(`Failed to update user phone: ${error.message}`);
  }
});

/**
 * Delete user account from both Auth and Firestore
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success message
 */
exports.deleteAccount = onCall(async (request) => {
  try {
    const {userId} = request.data;
    if (!userId) {
      throw new Error("User ID is required");
    }

    // Delete user data from Firestore
    await db.collection("users").doc(userId).delete();

    // Delete Firebase Auth user
    await auth.deleteUser(userId);

    return {success: true, message: "Account deleted successfully"};
  } catch (error) {
    logger.error("Error deleting account:", error);
    throw new Error(`Failed to delete account: ${error.message}`);
  }
});

/**
 * Send password reset email
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success message
 */
exports.sendPasswordResetEmail = onCall(async (request) => {
  try {
    const {email} = request.data;
    if (!email) {
      throw new Error("Email is required");
    }

    // In production, implement email service integration
    logger.info(`Password reset email requested for ${email}`);

    return {success: true, message: "Password reset email sent"};
  } catch (error) {
    logger.error("Error sending password reset email:", error);
    throw new Error(`Failed to send password reset email: ${error.message}`);
  }
});

/**
 * Trigger to clean up user data when user document is deleted
 * @param {Object} event - The deletion event
 * @return {Promise<void>}
 */
exports.onUserDeleted = onDocumentDeleted("users/{userId}", async (event) => {
  try {
    const {userId} = event.params;
    logger.info(`User document deleted for user: ${userId}`);
    // Additional cleanup logic can be added here
  } catch (error) {
    logger.error("Error in onUserDeleted trigger:", error);
    throw error;
  }
});

/**
 * Submit a new report to Firestore
 * Creates a new document with auto-generated ID in the 'reports' collection
 * @param {Object} request - The request object
 * @return {Promise<Object>} Document ID and success status
 */
exports.submitReport = onCall(async (request) => {
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
      status: "In progress",
      user: db.collection("users").doc(userId),
      // userId: userId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Add to Firestore and get document ID
    const docRef = await db.collection("reports").add(reportData);

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
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success status and message
 */
exports.uploadReportPhoto = onCall(async (request) => {
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
 * @param {Object} request - The request object
 * @return {Promise<Array>} Array of report objects with photos and names
 */
exports.getAllReports = onCall(async (request) => {
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
      if (reportData.user) {
        try {
          const userDoc = await reportData.user.get();
          if (userDoc.exists && userDoc.data().name) {
            userName = userDoc.data().name;
          }
        } catch (userError) {
          logger.warn(`Could not fetch user name from reference for ${
            reportId}`, userError);
        }
      }

      reports.push({
        documentId: reportId,
        description: reportData.description || "",
        hazardType: reportData.hazardType || "",
        localGov: reportData.localGov || "",
        locationDetails: reportData.locationDetails || "",
        mapsLocation: reportData.mapsLocation || null,
        status: reportData.status || "In progress",
        userName,
        userId: reportData.user.id || "",
        score: reportData.score || 0,
        createdAt: reportData.createdAt ? reportData.createdAt.toMillis() : 0,
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
 * Vote on a report (upvote or downvote)
 * Each user can only have one vote per report
 * @param {Object} request - The request object
 * @return {Promise<Object>} New score and user's vote status
 */
exports.voteReport = onCall(async (request) => {
  try {
    const {reportId, userId, voteType} = request.data;

    if (!reportId || !userId) {
      throw new Error("Report ID and User ID are required");
    }

    // voteType: 1 = upvote, -1 = downvote, 0 = remove vote
    if (voteType !== 1 && voteType !== -1 && voteType !== 0) {
      throw new Error("Invalid vote type. Use 1 (upvote), -1 (downvote), or 0 (remove)");
    }

    const reportRef = db.collection("reports").doc(reportId);
    const voteRef = reportRef.collection("votes").doc(userId);

    // Run as transaction to prevent race conditions
    const result = await db.runTransaction(async (transaction) => {
      const reportDoc = await transaction.get(reportRef);
      const voteDoc = await transaction.get(voteRef);

      if (!reportDoc.exists) {
        throw new Error("Report not found");
      }

      const currentScore = reportDoc.data().score || 0;
      const previousVote = voteDoc.exists ? voteDoc.data().vote : 0;

      // Calculate score change
      let scoreChange = 0;

      if (voteType === 0) {
        // Remove vote: subtract the previous vote
        scoreChange = -previousVote;
        transaction.delete(voteRef);
      } else if (previousVote === voteType) {
        // Same vote again: remove vote (toggle off)
        scoreChange = -previousVote;
        transaction.delete(voteRef);
      } else {
        // New vote or changing vote
        // If had previous vote, remove it first, then add new
        scoreChange = voteType - previousVote;
        transaction.set(voteRef, {vote: voteType});
      }

      const newScore = currentScore + scoreChange;
      transaction.update(reportRef, {score: newScore});

      // Determine user's current vote status after this action
      let userVote = 0;
      if (voteType !== 0 && previousVote !== voteType) {
        userVote = voteType;
      }

      return {newScore, userVote};
    });

    logger.info(`Vote processed for report ${reportId} by user ${userId}: ${result.userVote}`);
    return {
      success: true,
      score: result.newScore,
      userVote: result.userVote,
    };
  } catch (error) {
    logger.error("Error processing vote:", error);
    throw new Error(`Failed to process vote: ${error.message}`);
  }
});

/**
 * Get user's current vote status for a report
 * @param {Object} request - The request object
 * @return {Promise<Object>} User's vote status (1, -1, or 0)
 */
exports.getUserVote = onCall(async (request) => {
  try {
    const {reportId, userId} = request.data;

    if (!reportId || !userId) {
      throw new Error("Report ID and User ID are required");
    }

    const voteDoc = await db.collection("reports").doc(reportId)
        .collection("votes").doc(userId).get();

    const userVote = voteDoc.exists ? voteDoc.data().vote : 0;

    return {userVote};
  } catch (error) {
    logger.error("Error getting user vote:", error);
    throw new Error(`Failed to get user vote: ${error.message}`);
  }
});

/**
 * Get user's votes for multiple reports (batch)
 * @param {Object} request - The request object
 * @return {Promise<Object>} Map of reportId to vote status
 */
exports.getUserVotesForReports = onCall(async (request) => {
  try {
    const {reportIds, userId} = request.data;

    if (!reportIds || !userId || !Array.isArray(reportIds)) {
      throw new Error("Report IDs array and User ID are required");
    }

    const votes = {};

    // Fetch votes for all reports in parallel
    const votePromises = reportIds.map(async (reportId) => {
      const voteDoc = await db.collection("reports").doc(reportId)
          .collection("votes").doc(userId).get();
      votes[reportId] = voteDoc.exists ? voteDoc.data().vote : 0;
    });

    await Promise.all(votePromises);

    return {votes};
  } catch (error) {
    logger.error("Error getting user votes:", error);
    throw new Error(`Failed to get user votes: ${error.message}`);
  }
});

