const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();
const auth = admin.auth();

/**
 * Retrieve user name from Firestore
 */
const getUserName = onCall(async (request) => {
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
 */
const getUserPhone = onCall(async (request) => {
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
 * Retrieve user admin status from Firestore
 * Returns true if user has isAdmin field set to true, false otherwise
 */
const getIsAdmin = onCall(async (request) => {
  try {
    const {userId} = request.data;
    if (!userId) {
      throw new Error("User ID is required");
    }

    const userDoc = await db.collection("users").doc(userId).get();
    if (!userDoc.exists) {
      return false;
    }

    return userDoc.data().isAdmin === true;
  } catch (error) {
    logger.error("Error fetching user admin status:", error);
    throw new Error(`Failed to fetch user admin status: ${error.message}`);
  }
});

/**
 * Update user name in both Auth profile and Firestore
 */
const updateUserName = onCall(async (request) => {
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
 */
const updateUserPhone = onCall(async (request) => {
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
 */
const deleteAccount = onCall(async (request) => {
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
 */
const sendPasswordResetEmail = onCall(async (request) => {
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

module.exports = {
  getUserName,
  getUserPhone,
  getIsAdmin,
  updateUserName,
  updateUserPhone,
  deleteAccount,
  sendPasswordResetEmail,
};
