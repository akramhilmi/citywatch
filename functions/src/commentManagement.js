const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();

/**
 * Submit a new comment to a report
 */
const submitComment = onCall(async (request) => {
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
 */
const deleteComment = onCall(async (request) => {
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
 */
const getCommentsForReport = onCall(async (request) => {
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
 * Get the count of comments for a report
 */
const getCommentCount = onCall(async (request) => {
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

module.exports = {
  submitComment,
  deleteComment,
  getCommentsForReport,
  getCommentCount,
};
