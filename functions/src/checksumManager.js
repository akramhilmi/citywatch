const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

// Initialize Firebase Admin SDK (should only be done once)
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Update reports checksum when a report is created or modified
 * Includes usersVersion so cache is invalidated when referenced user data changes
 * @return {Promise<void>}
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
 * Update comments checksum for a specific report
 * Includes usersVersion so cache is invalidated when referenced user data changes
 * @param {string} reportId - The report ID
 * @return {Promise<void>}
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
 * Update global comments checksum
 * @return {Promise<void>}
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
 * Update usersVersion - triggers invalidation of caches
 * that reference user data (reports, comments)
 * @return {Promise<void>}
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
 * Update user profile checksum
 * @param {string} userId - The user ID
 * @return {Promise<void>}
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

module.exports = {
  updateReportsChecksum,
  updateCommentsChecksum,
  updateGlobalCommentsChecksum,
  updateUsersVersion,
  updateUserChecksum,
};
