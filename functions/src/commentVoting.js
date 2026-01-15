const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();

/**
 * Vote on a comment (upvote or downvote)
 * Each user can only have one vote per comment
 */
const voteComment = onCall(async (request) => {
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
 */
const getUserVotesForComments = onCall(async (request) => {
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

module.exports = {
  voteComment,
  getUserVotesForComments,
};
