/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendRelayChangeNotification = functions.firestore
  .document("/hdd-monitor/accounts/clients/client_1/panels/panel_1/relays/{relayId}")
  .onUpdate((change, context) => {
    const newValue = change.after.data();
    const oldValue = change.before.data();

    if (newValue.status !== oldValue.status) {
      const payload = {
        notification: {
          title: "Relay Status Changed",
          body: "${context.params.relayId} status has changed. Please check the system.",
          icon: "default",
          click_action: "FLUTTER_NOTIFICATION_CLICK"
        }
      };

      return admin.messaging().sendToTopic("relayStatusChanges", payload)
        .then((response) => {
          console.log("Notification sent successfully:", response);
          return null;
        })
        .catch((error) => {
          console.log("Error sending notification:", error);
        });
    }
    return null;
  });
