const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();

/**
 * Lightweight function to get all checksums
 * Client compares these with locally stored checksums
 */
const getChecksums = onCall(async (request) => {
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
 * Get cache checksums for efficient data update checking
 * Returns lightweight metadata about reports and comments
 * that clients can use to determine if cache needs refresh
 */
const getCacheChecksum = onCall(async (request) => {
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

module.exports = {
  getChecksums,
  getCacheChecksum,
};
