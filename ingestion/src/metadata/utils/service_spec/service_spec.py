"""
Manifests are used to store class information
"""

from typing import Optional, Type, cast

from pydantic import model_validator

from metadata.generated.schema.entity.services.serviceType import ServiceType
from metadata.ingestion.api.steps import Source
from metadata.ingestion.models.custom_pydantic import BaseModel
from metadata.utils.importer import get_module_dir, import_from_module


class BaseSpec(BaseModel):
    """
    # The OpenMetadata Ingestion Service Specification (Spec)

    This is the API for defining a service in OpenMetadata it needs to be in the classpath of the connector in
    the form:

    metadata.ingestion.source.{service_type}.{service_name}.service_spec.ServiceSpec

    Example for postres:

    metadata.ingestion.source.database.postgres.service_spec.ServiceSpec

    You can supply either strings with the full classpath or concrete classes that will be converted to strings.

    The use of strings for the values gives us a few advantages:
    1. manifests can be defined using json/yaml and deserialized into this class.
    2. We can dynamically import the class when needed and avoid dependency issues.
    3. We avoid circular imports.
    4. We can hot-swap the class implementation without changing the manifest (example: for testing).
    """

    profiler_class: Optional[str]
    metadata_source_class: str

    @model_validator(mode="before")
    @classmethod
    def transform_fields(cls, values):
        """This allows us to pass in the class directly instead of the string representation of the class. The
        validator will convert the class to a string representation of the class."""
        for field in list(cls.model_fields.keys()):
            if isinstance(values.get(field), type):
                values[field] = get_class_path(values[field])
        return values

    @classmethod
    def get_for_source(
        cls, service_type: ServiceType, source_type: str, from_: str = "ingestion"
    ) -> "BaseSpec":
        """Retrieves the manifest for a given source type. If it does not exist will attempt to retrieve
        a default manifest for the service type.

        Args:
            service_type (ServiceType): The service type.
            source_type (str): The source type.
            from_ (str, optional): The module to import from. Defaults to "ingestion".

        Returns:
            BaseSpec: The manifest for the source type.
        """
        return cls.model_validate(
            import_from_module(
                "metadata.{}.source.{}.{}.{}.ServiceSpec".format(  # pylint: disable=C0209
                    from_,
                    service_type.name.lower(),
                    get_module_dir(source_type),
                    "service_spec",
                )
            )
        )


def get_class_path(module):
    return module.__module__ + "." + module.__name__


def import_source_class(
    service_type: ServiceType, source_type: str, from_: str = "ingestion"
) -> Type[Source]:
    return cast(
        Type[Source],
        import_from_module(
            BaseSpec.get_for_source(
                service_type, source_type, from_
            ).metadata_source_class
        ),
    )