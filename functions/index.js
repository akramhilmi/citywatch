const {setGlobalOptions} = require("firebase-functions");
const {onCall} = require("firebase-functions/v2/https");
const {
  onDocumentDeleted,
  onDocumentCreated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
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

// ==================== Checksum Management ====================
// Checksums are stored in /metadata/checksums document
// Updated automatically via Firestore triggers on every change

/**
 * Update reports checksum when a report is created
 */
exports.onReportCreated = onDocumentCreated("reports/{reportId}", async () => {
  await updateReportsChecksum();
});

/**
 * Update reports checksum when a report is modified
 */
exports.onReportWritten = onDocumentWritten("reports/{reportId}", async () => {
  await updateReportsChecksum();
});

/**
 * Update comments checksum when a comment is created
 */
exports.onCommentCreated = onDocumentCreated(
    "comments/{commentId}",
    async (event) => {
      const data = event.data.data();
      if (data && data.report) {
        const reportId = data.report.id;
        await updateCommentsChecksum(reportId);
      }
      await updateGlobalCommentsChecksum();
    },
);

/**
 * Update comments checksum when a comment is modified
 */
exports.onCommentWritten = onDocumentWritten(
    "comments/{commentId}",
    async (event) => {
      const afterData = event.data.after.data();
      const beforeData = event.data.before.data();
      const reportId = afterData?.report?.id || beforeData?.report?.id;
      if (reportId) {
        await updateCommentsChecksum(reportId);
      }
      await updateGlobalCommentsChecksum();
    },
);

/**
 * Update user profile checksum when user data changes
 * Also updates global usersVersion to invalidate caches that reference users
 */
exports.onUserWritten = onDocumentWritten(
    "users/{userId}",
    async (event) => {
      const userId = event.params.userId;
      await updateUserChecksum(userId);
      // Update global users version - this will invalidate reports/comments
      // caches since they reference user data
      await updateUsersVersion();
    },
);

/**
 * Helper: Update reports checksum in metadata
 * Includes usersVersion so cache is invalidated when referenced user data changes
 */
async function updateReportsChecksum() {
  try {
    const snapshot = await db.collection("reports")
        .orderBy("createdAt", "desc")
        .limit(1)
        .get();
    const countSnapshot = await db.collection("reports").count().get();
    const count = countSnapshot.data().count;

    let latestTimestamp = 0;
    if (!snapshot.empty) {
      const data = snapshot.docs[0].data();
      if (data.createdAt) {
        latestTimestamp = data.createdAt.toMillis ?
            data.createdAt.toMillis() : data.createdAt;
      }
    }

    // Get current usersVersion to include in reports checksum
    const checksumDoc = await db.collection("metadata").doc("checksums").get();
    const usersVersion = checksumDoc.exists ?
        (checksumDoc.data().usersVersion || 0) : 0;

    // Composite checksum includes reports data + users version
    const checksum = `${count}_${latestTimestamp}_u${usersVersion}`;
    await db.collection("metadata").doc("checksums").set({
      reports: checksum,
      reportsUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});

    logger.info(`Reports checksum updated: ${checksum}`);
  } catch (error) {
    logger.error("Error updating reports checksum:", error);
  }
}

/**
 * Helper: Update comments checksum for a specific report
 * Includes usersVersion so cache is invalidated when referenced user data changes
 */
async function updateCommentsChecksum(reportId) {
  try {
    const reportRef = db.collection("reports").doc(reportId);
    const countSnapshot = await db.collection("comments")
        .where("report", "==", reportRef)
        .count()
        .get();
    const count = countSnapshot.data().count;

    const snapshot = await db.collection("comments")
        .where("report", "==", reportRef)
        .orderBy("datetime", "desc")
        .limit(1)
        .get();

    let latestTimestamp = 0;
    if (!snapshot.empty) {
      const data = snapshot.docs[0].data();
      if (data.datetime) {
        latestTimestamp = data.datetime.toMillis ?
            data.datetime.toMillis() : data.datetime;
      }
    }

    // Get current usersVersion to include in comments checksum
    const checksumDoc = await db.collection("metadata").doc("checksums").get();
    const usersVersion = checksumDoc.exists ?
        (checksumDoc.data().usersVersion || 0) : 0;

    // Composite checksum includes comments data + users version
    const checksum = `${count}_${latestTimestamp}_u${usersVersion}`;

    // Store in metadata/checksums under comments.{reportId}
    await db.collection("metadata").doc("checksums").set({
      [`comments_${reportId}`]: checksum,
    }, {merge: true});

    logger.info(`Comments checksum for ${reportId} updated: ${checksum}`);
  } catch (error) {
    logger.error(`Error updating comments checksum for ${reportId}:`, error);
  }
}

/**
 * Helper: Update global comments checksum
 */
async function updateGlobalCommentsChecksum() {
  try {
    const countSnapshot = await db.collection("comments").count().get();
    const count = countSnapshot.data().count;
    const checksum = `global_${count}_${Date.now()}`;

    await db.collection("metadata").doc("checksums").set({
      commentsGlobal: checksum,
    }, {merge: true});
  } catch (error) {
    logger.error("Error updating global comments checksum:", error);
  }
}

/**
 * Helper: Update usersVersion - triggers invalidation of caches
 * that reference user data (reports, comments)
 */
async function updateUsersVersion() {
  try {
    const newVersion = Date.now();

    await db.collection("metadata").doc("checksums").set({
      usersVersion: newVersion,
    }, {merge: true});

    logger.info(`Users version updated: ${newVersion}`);

    // Also update reports checksum since it includes usersVersion
    await updateReportsChecksum();

    // Update global comments checksum
    await updateGlobalCommentsChecksum();
  } catch (error) {
    logger.error("Error updating users version:", error);
  }
}

/**
 * Helper: Update user profile checksum
 */
async function updateUserChecksum(userId) {
  try {
    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) return;

    const checksum = `${userId}_${Date.now()}`;

    await db.collection("metadata").doc("checksums").set({
      [`user_${userId}`]: checksum,
    }, {merge: true});

    logger.info(`User checksum for ${userId} updated: ${checksum}`);
  } catch (error) {
    logger.error(`Error updating user checksum for ${userId}:`, error);
  }
}

/**
 * Lightweight function to get all checksums
 * Client compares these with locally stored checksums
 * @param {Object} request - The request object
 * @return {Promise<Object>} All checksums
 */
exports.getChecksums = onCall(async (request) => {
  try {
    const checksumDoc = await db.collection("metadata").doc("checksums").get();
    if (!checksumDoc.exists) {
      return {};
    }
    return checksumDoc.data();
  } catch (error) {
    logger.error("Error fetching checksums:", error);
    throw new Error(`Failed to fetch checksums: ${error.message}`);
  }
});

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
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      score: 0,
      comments: 0,
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
        userId: reportData.user.id || "",
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
      throw new Error(
          "Invalid vote type. Use 1 (upvote), -1 (downvote), or 0 (remove)");
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

    logger.info(
        `Vote for report ${reportId} by ${userId}: ${result.userVote}`,
    );
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

/**
 * Submit a new comment to a report
 * @param {Object} request - The request object
 * @return {Promise<Object>} Comment ID and success status
 */
exports.submitComment = onCall(async (request) => {
  try {
    const {content, reportId, userId} = request.data;

    if (!content || !reportId || !userId) {
      throw new Error("Content, report ID, and user ID are required");
    }

    // Validate that report exists
    const reportDoc = await db.collection("reports").doc(reportId).get();
    if (!reportDoc.exists) {
      throw new Error("Report not found");
    }

    // Validate that user exists
    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) {
      throw new Error("User not found");
    }

    // Create comment data with references
    const commentData = {
      content,
      datetime: admin.firestore.FieldValue.serverTimestamp(),
      report: db.collection("reports").doc(reportId),
      user: db.collection("users").doc(userId),
      score: 0,
    };

    // Add to Firestore
    const docRef = await db.collection("comments").add(commentData);

    // Increment comment count in report
    await db.collection("reports").doc(reportId).update({
      comments: admin.firestore.FieldValue.increment(1),
    });

    logger.info(`Comment created with ID: ${docRef.id} by user: ${userId}`);
    return {commentId: docRef.id, success: true};
  } catch (error) {
    logger.error("Error submitting comment:", error);
    throw new Error(`Failed to submit comment: ${error.message}`);
  }
});

/**
 * Delete a comment and decrement comment count
 * @param {Object} request - The request object
 * @return {Promise<Object>} Success message
 */
exports.deleteComment = onCall(async (request) => {
  try {
    const {commentId, userId} = request.data;

    if (!commentId || !userId) {
      throw new Error("Comment ID and user ID are required");
    }

    // Get the comment document
    const commentDoc = await db.collection("comments").doc(commentId).get();
    if (!commentDoc.exists) {
      throw new Error("Comment not found");
    }

    const commentData = commentDoc.data();

    // Verify user is the comment author
    if (commentData.user.id !== userId) {
      throw new Error("You can only delete your own comments");
    }

    // Get the report ID from the comment
    const reportRef = commentData.report;
    const reportId = reportRef.id;

    // Delete the comment
    await db.collection("comments").doc(commentId).delete();

    // Decrement comment count in report
    await db.collection("reports").doc(reportId).update({
      comments: admin.firestore.FieldValue.increment(-1),
    });

    logger.info(`Comment ${commentId} deleted by user ${userId}`);
    return {success: true, message: "Comment deleted successfully"};
  } catch (error) {
    logger.error("Error deleting comment:", error);
    throw new Error(`Failed to delete comment: ${error.message}`);
  }
});

/**
 * Get all comments for a specific report
 * @param {Object} request - The request object
 * @return {Promise<Array>} Array of comment objects
 */
exports.getCommentsForReport = onCall(async (request) => {
  try {
    const {reportId} = request.data;

    if (!reportId) {
      throw new Error("Report ID is required");
    }

    const reportRef = db.collection("reports").doc(reportId);
    const snapshot = await db.collection("comments")
        .where("report", "==", reportRef)
        .orderBy("datetime", "desc")
        .get();

    if (snapshot.empty) {
      logger.info(`No comments found for report: ${reportId}`);
      return [];
    }

    const comments = [];

    for (const doc of snapshot.docs) {
      const commentData = doc.data();
      const commentId = doc.id;

      // Get user name from user reference
      let userName = "Anonymous";
      let userId = "";
      if (commentData.user) {
        try {
          userId = commentData.user.id;
          const userDoc = await commentData.user.get();
          if (userDoc.exists && userDoc.data().name) {
            userName = userDoc.data().name;
          }
        } catch (userError) {
          logger.warn(`Could not fetch user for comment ${commentId}`,
              userError);
        }
      }

      comments.push({
        commentId,
        content: commentData.content || "",
        datetime: commentData.datetime ?
            commentData.datetime.toMillis() : 0,
        reportId,
        userId,
        userName,
        score: commentData.score || 0,
      });
    }

    logger.info(`Fetched ${comments.length} comments for report: ${reportId}`);
    return comments;
  } catch (error) {
    logger.error("Error fetching comments:", error);
    throw new Error(`Failed to fetch comments: ${error.message}`);
  }
});

/**
 * Vote on a comment (upvote or downvote)
 * Each user can only have one vote per comment
 * @param {Object} request - The request object
 * @return {Promise<Object>} New score and user's vote status
 */
exports.voteComment = onCall(async (request) => {
  try {
    const {commentId, userId, voteType} = request.data;

    if (!commentId || !userId) {
      throw new Error("Comment ID and User ID are required");
    }

    // voteType: 1 = upvote, -1 = downvote, 0 = remove vote
    if (voteType !== 1 && voteType !== -1 && voteType !== 0) {
      throw new Error(
          "Invalid vote type. Use 1 (upvote), -1 (downvote), or 0 (remove)");
    }

    const commentRef = db.collection("comments").doc(commentId);
    const voteRef = commentRef.collection("votes").doc(userId);

    // Run as transaction to prevent race conditions
    const result = await db.runTransaction(async (transaction) => {
      const commentDoc = await transaction.get(commentRef);
      const voteDoc = await transaction.get(voteRef);

      if (!commentDoc.exists) {
        throw new Error("Comment not found");
      }

      const currentScore = commentDoc.data().score || 0;
      const previousVote = voteDoc.exists ? voteDoc.data().vote : 0;

      // Calculate score change
      let scoreChange = 0;

      if (voteType === 0) {
        // Remove vote
        scoreChange = -previousVote;
        transaction.delete(voteRef);
      } else if (previousVote === voteType) {
        // Same vote again: toggle off
        scoreChange = -previousVote;
        transaction.delete(voteRef);
      } else {
        // New vote or changing vote
        scoreChange = voteType - previousVote;
        transaction.set(voteRef, {vote: voteType});
      }

      const newScore = currentScore + scoreChange;
      transaction.update(commentRef, {score: newScore});

      // Determine user's current vote status after this action
      let userVote = 0;
      if (voteType !== 0 && previousVote !== voteType) {
        userVote = voteType;
      }

      return {newScore, userVote};
    });

    logger.info(
        `Comment vote processed for ${commentId} by user ${userId}: ${
          result.userVote}`);
    return {
      success: true,
      score: result.newScore,
      userVote: result.userVote,
    };
  } catch (error) {
    logger.error("Error processing comment vote:", error);
    throw new Error(`Failed to process vote: ${error.message}`);
  }
});

/**
 * Get user's votes for multiple comments (batch)
 * @param {Object} request - The request object
 * @return {Promise<Object>} Map of commentId to vote status
 */
exports.getUserVotesForComments = onCall(async (request) => {
  try {
    const {commentIds, userId} = request.data;

    if (!commentIds || !userId || !Array.isArray(commentIds)) {
      throw new Error("Comment IDs array and User ID are required");
    }

    const votes = {};

    // Fetch votes for all comments in parallel
    const votePromises = commentIds.map(async (commentId) => {
      const voteDoc = await db.collection("comments").doc(commentId)
          .collection("votes").doc(userId).get();
      votes[commentId] = voteDoc.exists ? voteDoc.data().vote : 0;
    });

    await Promise.all(votePromises);

    return {votes};
  } catch (error) {
    logger.error("Error getting user votes for comments:", error);
    throw new Error(`Failed to get user votes: ${error.message}`);
  }
});

/**
 * Get the count of comments for a report
 * @param {Object} request - The request object
 * @return {Promise<Object>} Comment count
 */
exports.getCommentCount = onCall(async (request) => {
  try {
    const {reportId} = request.data;

    if (!reportId) {
      throw new Error("Report ID is required");
    }

    const reportRef = db.collection("reports").doc(reportId);
    const snapshot = await db.collection("comments")
        .where("report", "==", reportRef)
        .count()
        .get();

    const count = snapshot.data().count;
    logger.info(`Comment count for report ${reportId}: ${count}`);
    return {count};
  } catch (error) {
    logger.error("Error getting comment count:", error);
    throw new Error(`Failed to get comment count: ${error.message}`);
  }
});

/**
 * Get cache checksums for efficient data update checking
 * Returns lightweight metadata about reports and comments
 * that clients can use to determine if cache needs refresh
 * @param {Object} request - The request object
 * @return {Promise<Object>} Cache checksums and counts
 */
exports.getCacheChecksum = onCall(async (request) => {
  try {
    const {dataType, reportId} = request.data;

    if (dataType === "reports") {
      // Get report count and latest update timestamp
      const reportsSnapshot = await db.collection("reports")
          .orderBy("createdAt", "desc")
          .limit(1)
          .get();

      const countSnapshot = await db.collection("reports").count().get();
      const reportCount = countSnapshot.data().count;

      let latestTimestamp = 0;
      if (!reportsSnapshot.empty) {
        const latestDoc = reportsSnapshot.docs[0];
        const data = latestDoc.data();
        if (data.createdAt) {
          latestTimestamp = data.createdAt.toMillis ?
              data.createdAt.toMillis() : data.createdAt;
        }
      }

      // Generate a simple checksum based on count + latest timestamp
      const checksum = `${reportCount}_${latestTimestamp}`;

      return {
        checksum,
        count: reportCount,
        latestTimestamp,
      };
    } else if (dataType === "comments" && reportId) {
      const reportRef = db.collection("reports").doc(reportId);

      // Get comment count for this report
      const countSnapshot = await db.collection("comments")
          .where("report", "==", reportRef)
          .count()
          .get();
      const commentCount = countSnapshot.data().count;

      // Get latest comment timestamp
      const commentsSnapshot = await db.collection("comments")
          .where("report", "==", reportRef)
          .orderBy("datetime", "desc")
          .limit(1)
          .get();

      let latestTimestamp = 0;
      if (!commentsSnapshot.empty) {
        const latestDoc = commentsSnapshot.docs[0];
        const data = latestDoc.data();
        if (data.datetime) {
          latestTimestamp = data.datetime.toMillis ?
              data.datetime.toMillis() : data.datetime;
        }
      }

      const checksum = `${commentCount}_${latestTimestamp}`;

      return {
        checksum,
        count: commentCount,
        latestTimestamp,
      };
    } else {
      throw new Error("Invalid dataType or missing reportId for comments");
    }
  } catch (error) {
    logger.error("Error getting cache checksum:", error);
    throw new Error(`Failed to get cache checksum: ${error.message}`);
  }
});

