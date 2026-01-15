const {onDocumentDeleted, onDocumentCreated, onDocumentWritten} =
  require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {
  updateReportsChecksum,
  updateCommentsChecksum,
  updateGlobalCommentsChecksum,
  updateUsersVersion,
} = require("./checksumManager");

/**
 * Update reports checksum when a report is created
 */
const onReportCreated = onDocumentCreated(
    "reports/{reportId}",
    async () => {
      await updateReportsChecksum();
    },
);

/**
 * Update reports checksum when a report is modified
 */
const onReportWritten = onDocumentWritten(
    "reports/{reportId}",
    async () => {
      await updateReportsChecksum();
    },
);

/**
 * Update comments checksum when a comment is created
 */
const onCommentCreated = onDocumentCreated(
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
const onCommentWritten = onDocumentWritten(
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
const onUserWritten = onDocumentWritten(
    "users/{userId}",
    async (event) => {
      await updateUsersVersion();
    },
);

/**
 * Trigger to clean up user data when user document is deleted
 */
const onUserDeleted = onDocumentDeleted("users/{userId}", async (event) => {
  try {
    const {userId} = event.params;
    logger.info(`User document deleted for user: ${userId}`);
    // Additional cleanup logic can be added here
  } catch (error) {
    logger.error("Error in onUserDeleted trigger:", error);
    throw error;
  }
});

module.exports = {
  onReportCreated,
  onReportWritten,
  onCommentCreated,
  onCommentWritten,
  onUserWritten,
  onUserDeleted,
};
