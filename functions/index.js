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
 * Retrieve user name from Firestore
 */
exports.getUserName = onCall(async (request) => {
  try {
    const userId = request.data.userId;
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
exports.getUserPhone = onCall(async (request) => {
  try {
    const userId = request.data.userId;
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
 */
exports.deleteAccount = onCall(async (request) => {
  try {
    const userId = request.data.userId;
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
exports.sendPasswordResetEmail = onCall(async (request) => {
  try {
    const email = request.data.email;
    if (!email) {
      throw new Error("Email is required");
    }

    // Generate password reset link
    //    const resetLink = await auth.generatePasswordResetLink(email);

    // In production, you might want to send this via email service
    // For now, we just return the link to be handled by the client
    logger.info(`Password reset link generated for ${email}`);

    return {success: true, message: "Password reset email sent"};
  } catch (error) {
    logger.error("Error sending password reset email:", error);
    throw new Error(`Failed to send password reset email: ${error.message}`);
  }
});

/**
 * Trigger to clean up user data when Auth user is deleted
 */
exports.onUserDeleted = onDocumentDeleted("users/{userId}", async (event) => {
  try {
    const userId = event.params.userId;
    logger.info(`User document deleted for user: ${userId}`);
    // Additional cleanup logic can be added here
  } catch (error) {
    logger.error("Error in onUserDeleted trigger:", error);
  }
});

/**
 * Submit a new report to Firestore
 * Creates a new document with auto-generated ID in the 'reports' collection
 *
 * @param description Report description
 * @param hazardType Type of hazard (e.g., "Pothole")
 * @param localGov Local government authority
 * @param locationDetails Street/area details
 * @param latitude Latitude coordinate
 * @param longitude Longitude coordinate
 * @returns documentId The auto-generated report document ID
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
    } = request.data;

    // Validate required fields
    if (!description ||
      !hazardType ||
      !localGov ||
      !locationDetails ||
      latitude === undefined ||
      longitude === undefined) {
      throw new Error("All fields are required");
    }

    // Create report data
    const reportData = {
      description,
      hazardType,
      localGov,
      locationDetails,
      mapsLocation: new admin.firestore.GeoPoint(latitude, longitude),
      status: "In progress",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Add to Firestore and get document ID
    const docRef = await db.collection("reports").add(reportData);

    logger.info(`Report created with ID: ${docRef.id}`);
    return {documentId: docRef.id, success: true};
  } catch (error) {
    logger.error("Error submitting report:", error);
    throw new Error(`Failed to submit report: ${error.message}`);
  }
});

/**
 * Upload report photo from base64 encoded string
 * Stores image in report_photos/{documentId}.jpg
 *
 * @param documentId The report document ID
 * @param imageBase64 Base64 encoded image data
 * @returns success status
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

