from metadata.ingestion.source.pipeline.kafkaconnect.metadata import KafkaconnectSource
from metadata.utils.service_spec import BaseSpec

ServiceSpec = BaseSpec(metadata_source_class=KafkaconnectSource)