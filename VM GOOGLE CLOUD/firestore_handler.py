from google.cloud import firestore
import logging
from typing import Dict, Any, Optional
from datetime import datetime
import pytz
from notification_handler import NotificationHandler

class FirestoreHandler:
    def __init__(self):
        self.db = firestore.Client(project='fir-hdd-monitor-d00de')
        self.notification_handler = NotificationHandler(self.db)
        self._watch_references = []
        self._relay_states = {}  # Caché para estados de relays
        self._initial_load_complete = False
        self._events_initial_snapshots = set()  # Para rastrear snapshots iniciales

    def _cache_relay_state(self, relay_path: str, state: Dict[str, Any]):
        """Almacena el estado de un relay en el caché local"""
        self._relay_states[relay_path] = {
            'status': state.get('status'),
            'lastUpdate': state.get('lastUpdate'),
            'name': state.get('name', '')
        }

    def _get_cached_relay_state(self, relay_path: str) -> Optional[Dict[str, Any]]:
        """Obtiene el estado cacheado de un relay"""
        return self._relay_states.get(relay_path)

    def watch_events(self):
        """Observa cambios en documentos de eventos para cada cliente"""
        try:
            # Obtener todos los clientes
            clients_ref = self.db.collection('hdd-monitor/accounts/clients')
            clients = clients_ref.stream()

            for client in clients:
                def create_snapshot_handler(client_id):
                    snapshot_key = f"events_{client_id}"
                    initial_snapshot_processed = False
                    last_snapshot = {}  # Almacenar el snapshot anterior
                    
                    def on_snapshot(doc_snapshot, changes, read_time):
                        nonlocal initial_snapshot_processed, last_snapshot
                        
                        # Solo loggear la carga inicial una vez
                        if not initial_snapshot_processed:
                            initial_snapshot_processed = True
                            # Guardar el estado inicial de los documentos
                            for doc in doc_snapshot:
                                last_snapshot[doc.id] = doc.to_dict()
                            logging.info(f"Carga inicial de eventos para cliente {client_id}")
                            return

                        # Procesar todos los cambios después de la carga inicial
                        for change in changes:
                            try:
                                doc = change.document
                                new_data = doc.to_dict() if change.type.name != 'REMOVED' else None
                                old_data = last_snapshot.get(doc.id, {})

                                if change.type.name == 'ADDED':
                                    if new_data:
                                        logging.info(f"Nuevo evento detectado:")
                                        logging.info(f"ID: {doc.id}")
                                        logging.info(f"Estado inicial: {new_data.get('status')}")
                                        self.notification_handler.process_event_update(
                                            doc.reference,
                                            {},
                                            new_data
                                        )
                                        last_snapshot[doc.id] = new_data
                                
                                elif change.type.name == 'MODIFIED':
                                    logging.info(f"Evento modificado detectado:")
                                    logging.info(f"ID: {doc.id}")
                                    logging.info(f"Estado anterior: {old_data.get('status')}")
                                    logging.info(f"Nuevo estado: {new_data.get('status')}")
                                    
                                    self.notification_handler.process_event_update(
                                        doc.reference,
                                        old_data,
                                        new_data
                                    )
                                    last_snapshot[doc.id] = new_data
                                
                                elif change.type.name == 'REMOVED':
                                    logging.info(f"Evento eliminado detectado: {doc.id}")
                                    self.notification_handler.process_event_update(
                                        doc.reference,
                                        last_snapshot.get(doc.id, {}),
                                        None
                                    )
                                    last_snapshot.pop(doc.id, None)
                                                
                            except Exception as e:
                                logging.error(f"Error procesando cambio de evento: {e}", exc_info=True)

                        # Actualizar el snapshot con el estado actual de todos los documentos
                        current_snapshot = {doc.id: doc.to_dict() for doc in doc_snapshot}
                        last_snapshot.update(current_snapshot)
                        
                    return on_snapshot

                # Observar colección de eventos de cada cliente
                events_ref = clients_ref.document(client.id).collection('events')
                watch = events_ref.on_snapshot(create_snapshot_handler(client.id))
                self._watch_references.append(watch)
                logging.info(f"Observador de eventos iniciado para cliente {client.id}")

        except Exception as e:
            logging.error(f"Error iniciando observadores de eventos: {e}", exc_info=True)

    def watch_relay_states(self):
        """Observa cambios en estados de relays en todos los paneles"""
        def on_snapshot(doc_snapshot, changes, read_time):
            if not self._initial_load_complete:
                # Cargar estado inicial de todos los relays
                for doc in doc_snapshot:
                    self._cache_relay_state(doc.reference.path, doc.to_dict())
                self._initial_load_complete = True
                logging.info("Estado inicial de relays cargado")
                return

            for change in changes:
                try:
                    if change.type.name == 'MODIFIED':
                        doc = change.document
                        new_data = doc.to_dict()
                        relay_path = doc.reference.path
                        
                        # Obtener estado anterior del caché
                        cached_state = self._get_cached_relay_state(relay_path)
                        
                        logging.info(f"Cambio detectado en relay: {doc.id}")
                        logging.info(f"Estado anterior (caché): {cached_state.get('status') if cached_state else None}")
                        logging.info(f"Nuevo estado: {new_data.get('status')}")
                        
                        # Solo procesar si hay un cambio real en el estado
                        if (cached_state and 
                            cached_state.get('status') != new_data.get('status') and
                            new_data.get('source', '') != 'mqtt'):
                            
                            logging.info(f"Procesando cambio real de estado en relay {doc.id}")
                            self.notification_handler.process_relay_update(
                                doc.reference,
                                {'status': cached_state.get('status')},
                                new_data
                            )
                            
                        # Actualizar caché con el nuevo estado
                        self._cache_relay_state(relay_path, new_data)
                        
                except Exception as e:
                    logging.error(f"Error procesando cambio de relay: {e}", exc_info=True)

        try:
            # Obtener todos los clientes
            clients_ref = self.db.collection('hdd-monitor/accounts/clients')
            clients = clients_ref.stream()

            for client in clients:
                # Obtener todos los paneles de cada cliente
                panels_ref = clients_ref.document(client.id).collection('panels')
                panels = panels_ref.stream()

                for panel in panels:
                    # Observar colección de relays de cada panel
                    relays_ref = panels_ref.document(panel.id).collection('relays')
                    watch = relays_ref.on_snapshot(on_snapshot)
                    self._watch_references.append(watch)
                    logging.info(f"Observador de relays iniciado para panel {panel.id} del cliente {client.id}")

        except Exception as e:
            logging.error(f"Error iniciando observadores de relays: {e}", exc_info=True)

    def handle_panel_message(self, topic: str, payload: Dict[str, Any]):
        """Maneja mensajes MQTT de paneles"""
        try:
            parts = topic.split('/')
            if len(parts) < 4 or parts[0] != "clients" or parts[2] != "panels":
                logging.warning(f"Formato de tópico inválido: {topic}")
                return

            client_id = parts[1]
            panel_id = parts[3]
            
            if 'relay' in payload and 'state' in payload:
                self._update_relay_state(client_id, panel_id, payload)
                
        except Exception as e:
            logging.error(f"Error en handle_panel_message: {e}", exc_info=True)

    def _update_relay_state(self, client_id: str, panel_id: str, payload: Dict[str, Any]):
        """Actualiza el estado de un relay"""
        try:
            relay_name = payload['relay']
            new_state = payload['state']
            
            panel_ref = self.db.document(f'hdd-monitor/accounts/clients/{client_id}/panels/{panel_id}')
            relay_ref = panel_ref.collection('relays').document(relay_name)
            
            # Verificar panel
            panel_snap = panel_ref.get()
            if not panel_snap.exists:
                logging.error(f"Panel no encontrado: {panel_id}")
                return
                
            # Obtener estado anterior
            relay_snap = relay_ref.get()
            old_data = relay_snap.to_dict() if relay_snap.exists else {'status': None}
            
            # Actualizar estado
            new_data = {
                'status': new_state,
                'date_time': datetime.now(pytz.timezone('America/Bogota')).strftime('%d/%m/%Y, %H:%M'),
                'lastUpdate': firestore.SERVER_TIMESTAMP,
                'source': 'mqtt'  # Identificar la fuente del cambio
            }
            relay_ref.set(new_data, merge=True)
            
            # Procesar notificación si cambió el estado
            if old_data.get('status') != new_state:
                self.notification_handler.process_relay_update(relay_ref, old_data, new_data)
                
        except Exception as e:
            logging.error(f"Error en _update_relay_state: {e}", exc_info=True)

    def cleanup(self):
        """Limpia los observadores al cerrar"""
        for watch in self._watch_references:
            try:
                watch.unsubscribe()
            except Exception as e:
                logging.error(f"Error al limpiar observador: {e}")
        self._watch_references.clear()