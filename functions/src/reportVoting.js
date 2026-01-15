const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();

/**
 * Vote on a report (upvote or downvote)
 * Each user can only have one vote per report
 */
const voteReport = onCall(async (request) => {
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
 */
const getUserVote = onCall(async (request) => {
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
 */
const getUserVotesForReports = onCall(async (request) => {
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

module.exports = {
  voteReport,
  getUserVote,
  getUserVotesForReports,
};
