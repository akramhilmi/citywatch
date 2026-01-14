const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();

/**
 * Get all report votes for a specific user
 * Returns a map of reportId -> voteType
 */
const getAllReportVotesForUser = onCall(async (request) => {
  try {
    const {userId} = request.data;

    if (!userId) {
      throw new Error("User ID is required");
    }

    const allReports = await db.collection("reports").get();
    const votesMap = {};

    for (const reportDoc of allReports.docs) {
      const reportRef = db.collection("reports").doc(reportDoc.id);
      const voteDoc = await reportRef.collection("votes").doc(userId).get();

      if (voteDoc.exists) {
        votesMap[reportDoc.id] = voteDoc.data().vote;
      } else {
        votesMap[reportDoc.id] = 0;
      }
    }

    logger.info(`Fetched all report votes for user ${userId}: ${
      Object.keys(votesMap).length} reports`);
    return {votes: votesMap};
  } catch (error) {
    logger.error("Error fetching all report votes:", error);
    throw new Error(`Failed to fetch votes: ${error.message}`);
  }
});

/**
 * Get all comment votes for a specific user
 * Returns a map of commentId -> voteType
 */
const getAllCommentVotesForUser = onCall(async (request) => {
  try {
    const {userId} = request.data;

    if (!userId) {
      throw new Error("User ID is required");
    }

    const allComments = await db.collection("comments").get();
    const votesMap = {};

    for (const commentDoc of allComments.docs) {
      const commentRef = db.collection("comments").doc(commentDoc.id);
      const voteDoc = await commentRef.collection("votes").doc(userId).get();

      if (voteDoc.exists) {
        votesMap[commentDoc.id] = voteDoc.data().vote;
      } else {
        votesMap[commentDoc.id] = 0;
      }
    }

    logger.info(`Fetched all comment votes for user ${userId}: ${
      Object.keys(votesMap).length} comments`);
    return {votes: votesMap};
  } catch (error) {
    logger.error("Error fetching all comment votes:", error);
    throw new Error(`Failed to fetch votes: ${error.message}`);
  }
});

module.exports = {
  getAllReportVotesForUser,
  getAllCommentVotesForUser,
};
