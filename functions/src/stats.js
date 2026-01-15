const {onCall} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const db = admin.firestore();
const statsRef = db.collection("metadata").doc("stats");

/**
 * Get all stats
 */
const getStats = onCall(async (request) => {
    try {
        const doc = await statsRef.get();
        if (!doc.exists) {
            logger.info("Stats document not found, creating it.");
            await statsRef.set({
                confirmed: 0,
                inProgress: 0,
                resolved: 0,
                submitted: 0,
            });
            const newDoc = await statsRef.get();
            return newDoc.data();
        }
        return doc.data();
    } catch (error) {
        logger.error("Error getting stats:", error);
        throw new Error(`Failed to get stats: ${error.message}`);
    }
});

/**
 * Increment a specific stat value
 */
const incrementStat = onCall(async (request) => {
    const {stat} = request.data;
    if (!stat || !["confirmed", "inProgress", "resolved", "submitted"].includes(stat)) {
        throw new Error("Invalid stat name provided.");
    }

    try {
        await statsRef.update({
            [stat]: admin.firestore.FieldValue.increment(1),
        });
        logger.info(`Incremented stat: ${stat}`);
        return {success: true};
    } catch (error) {
        logger.error(`Error incrementing stat ${stat}:`, error);
        throw new Error(`Failed to increment stat: ${error.message}`);
    }
});

/**
 * Decrement a specific stat value
 */
const decrementStat = onCall(async (request) => {
    const {stat} = request.data;
    if (!stat || !["confirmed", "inProgress", "resolved", "submitted"].includes(stat)) {
        throw new Error("Invalid stat name provided.");
    }

    try {
        await statsRef.update({
            [stat]: admin.firestore.FieldValue.increment(-1),
        });
        logger.info(`Decremented stat: ${stat}`);
        return {success: true};
    } catch (error) {
        logger.error(`Error decrementing stat ${stat}:`, error);
        throw new Error(`Failed to decrement stat: ${error.message}`);
    }
});

module.exports = {
    getStats,
//    incrementStat,
//    decrementStat,
};
