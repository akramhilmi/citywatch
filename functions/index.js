const {setGlobalOptions} = require("firebase-functions");
const admin = require("firebase-admin");

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  admin.initializeApp();
}

setGlobalOptions({
  maxInstances: 10,
});

// ==================== Import all modules ====================

// Triggers
// Mainly for updating checksums and user versions
const {
  onReportCreated,
  onReportWritten,
  onCommentCreated,
  onCommentWritten,
  onUserWritten,
  onUserDeleted,
} = require("./src/triggers");

// User Management
const {
  getUserName,
  getUserPhone,
  getIsAdmin,
  updateUserName,
  updateUserPhone,
  deleteAccount,
  sendPasswordResetEmail,
} = require("./src/userManagement");

// Report Management
const {
  submitReport,
  uploadReportPhoto,
  getAllReports,
  editReport,
  deleteReport,
} = require("./src/reportManagement");

// Report Voting
const {
  voteReport,
  getUserVote,
  getUserVotesForReports,
} = require("./src/reportVoting");

// Comment Management
const {
  submitComment,
  deleteComment,
  editComment,
  getCommentsForReport,
  getCommentCount,
} = require("./src/commentManagement");

// Comment Voting
const {
  voteComment,
  getUserVotesForComments,
} = require("./src/commentVoting");

// User Voting (fetch all votes on startup)
const {
  getAllReportVotesForUser,
  getAllCommentVotesForUser,
} = require("./src/userVoting");

// Caching
const {
  getChecksums,
  getCacheChecksum,
} = require("./src/caching");

// Stats
const {
  getStats,
  incrementStat,
  decrementStat,
} = require("./src/stats");

// ==================== Export all functions ====================

// Triggers
exports.onReportCreated = onReportCreated;
exports.onReportWritten = onReportWritten;
exports.onCommentCreated = onCommentCreated;
exports.onCommentWritten = onCommentWritten;
exports.onUserWritten = onUserWritten;
exports.onUserDeleted = onUserDeleted;

// User Management
exports.getUserName = getUserName;
exports.getUserPhone = getUserPhone;
exports.getIsAdmin = getIsAdmin;
exports.updateUserName = updateUserName;
exports.updateUserPhone = updateUserPhone;
exports.deleteAccount = deleteAccount;
exports.sendPasswordResetEmail = sendPasswordResetEmail;

// Report Management
exports.submitReport = submitReport;
exports.uploadReportPhoto = uploadReportPhoto;
exports.getAllReports = getAllReports;
exports.editReport = editReport;
exports.deleteReport = deleteReport;

// Report Voting
exports.voteReport = voteReport;
exports.getUserVote = getUserVote;
exports.getUserVotesForReports = getUserVotesForReports;

// Comment Management
exports.submitComment = submitComment;
exports.deleteComment = deleteComment;
exports.editComment = editComment;
exports.getCommentsForReport = getCommentsForReport;
exports.getCommentCount = getCommentCount;

// Comment Voting
exports.voteComment = voteComment;
exports.getUserVotesForComments = getUserVotesForComments;

// User Voting
exports.getAllReportVotesForUser = getAllReportVotesForUser;
exports.getAllCommentVotesForUser = getAllCommentVotesForUser;

// Caching
exports.getChecksums = getChecksums;
exports.getCacheChecksum = getCacheChecksum;

// Stats
exports.getStats = getStats;
exports.incrementStat = incrementStat;
exports.decrementStat = decrementStat;
