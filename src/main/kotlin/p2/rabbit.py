import pika
import json
import uuid
import random
import time
import threading

# RabbitMQ connection parameters
RABBITMQ_HOST = 'localhost'
RABBITMQ_PORT = 5672
RABBITMQ_QUEUE = 'Command'

# Connect to RabbitMQ
connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT))
channel = connection.channel()

# Declare the queue
channel.queue_declare(queue=RABBITMQ_QUEUE)

def generate_insight():
    return {
        "objectUid": str(uuid.uuid4()),
        "state": str(random.randint(0, 1)),
        "positionX": random.randint(0, 1024),
        "positionY": random.randint(0, 768),
        "timestamp": int(time.time() * 1000),
        "zoneUid": random.choice(['A', 'B', 'C', 'D']),
        "sectorUid": random.choice(['A1', 'A2', 'B1', 'B2']),
        "threatCategory": random.randint(0, 3),
        "camUid": str(random.randint(1, 10))
    }

def publish_messages():
    while True:
        for _ in range(600):
            insight = generate_insight()
            message = json.dumps(insight)
            channel.basic_publish(exchange='', routing_key=RABBITMQ_QUEUE, body=message)
            print(f"Sent: {message}")
        time.sleep(1)  # Sleep for 1 second to achieve 100 messages per second

# Start publishing messages in a separate thread
publisher_thread = threading.Thread(target=publish_messages)
publisher_thread.start()
