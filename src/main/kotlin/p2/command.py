import pika

# RabbitMQ connection parameters
RABBITMQ_HOST = 'localhost'
RABBITMQ_PORT = 5672
RABBITMQ_QUEUE = 'Command'

# Connect to RabbitMQ
connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT))
channel = connection.channel()

# Declare the queue
channel.queue_declare(queue=RABBITMQ_QUEUE)

def callback(ch, method, properties, body):
    print(f"Received: {body}")

# Set up subscription on the queue
channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=callback, auto_ack=True)

print('Waiting for messages. To exit press CTRL+C')
channel.start_consuming()
