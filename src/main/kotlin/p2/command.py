import pika
import json

RABBITMQ_HOST = 'localhost'
RABBITMQ_PORT = 5672
RABBITMQ_QUEUE = 'Command'

connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT))
channel = connection.channel()

channel.queue_declare(queue=RABBITMQ_QUEUE)

def callback(ch, method, properties, body):
    insight = json.loads(body.decode('utf-8'))
    print(f"Received Insight: {insight}")

channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=callback, auto_ack=True)

print('Waiting for messages. To exit press CTRL+C')
channel.start_consuming()
