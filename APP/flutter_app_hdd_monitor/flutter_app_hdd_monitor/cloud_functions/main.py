from firebase_functions import firestore_trigger

def process_message(data, context):
    name = data['name']
    status = data['status']
    date_time = data['date_time']

    db = firestore_trigger.firestore.client()
    relay_ref = db.collection('hdd-monitor') \
                  .document('accounts') \
                  .collection('clients') \
                  .document('client_1') \
                  .collection('panels') \
                  .document('panel_1') \
                  .collection('relays') \
                  .document(name)

    relay_ref.set({
        'status': status,
        'date_time': date_time
    }, merge=True)
