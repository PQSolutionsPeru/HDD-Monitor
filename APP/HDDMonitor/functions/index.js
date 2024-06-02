const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendRelayNotification = functions.firestore
    .document("hdd-monitor/accounts/clients/{clientId}/panels/{panelId}/relays/{relayId}")
    .onUpdate((change, context) => {
        const newValue = change.after.data();
        const previousValue = change.before.data();

        if (newValue.status !== previousValue.status) {
            const payload = {
                notification: {
                    title: "Relay Status Changed",
                    body: `The status of relay ${context.params.relayId} has changed to ${newValue.status}`,
                },
                topic: "relay-status"
            };

            return admin.messaging().send(payload)
                .then((response) => {
                    console.log("Successfully sent message:", response);
                })
                .catch((error) => {
                    console.log("Error sending message:", error);
                });
        }

        return null;
    });
