package org.beckn.one.sandbox.bap.client.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.beckn.one.sandbox.bap.client.dtos.ClientSearchResponse
import org.beckn.one.sandbox.bap.client.dtos.ClientSearchResponseMessage
import org.beckn.one.sandbox.bap.message.entities.Message
import org.beckn.one.sandbox.bap.message.entities.SearchResponse
import org.beckn.one.sandbox.bap.message.entities.SearchResponseMessage
import org.beckn.one.sandbox.bap.message.factories.CatalogFactory
import org.beckn.one.sandbox.bap.message.mappers.CatalogMapper
import org.beckn.one.sandbox.bap.message.mappers.ContextMapper
import org.beckn.one.sandbox.bap.message.repositories.GenericRepository
import org.beckn.one.sandbox.bap.schemas.ProtocolCatalog
import org.beckn.one.sandbox.bap.schemas.factories.ContextFactory
import org.beckn.one.sandbox.bap.schemas.factories.UuidFactory
import org.hamcrest.CoreMatchers.`is`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(value = ["test"])
@TestPropertySource(locations = ["/application-test.yml"])
class SearchControllerOnSearchSpec @Autowired constructor(
  val mockMvc: MockMvc,
  val objectMapper: ObjectMapper,
  val uuidFactory: UuidFactory,
  val contextFactory: ContextFactory,
  val contextMapper: ContextMapper,
  val catalogMapper: CatalogMapper,
  val messageRepository: GenericRepository<Message>,
  val searchResponseRepository: GenericRepository<SearchResponse>
) : DescribeSpec() {

  init {

    describe("On Search") {
      val catalogFactory = CatalogFactory()

      it("should return error bad request response when message id is null") {
        mockMvc
          .perform(
            get("/client/v1/on_search")
              .param("messageId", null)
          )
          .andExpect(status().isBadRequest)
      }

      it("should return error response when message id is invalid") {
        val nonExistentMessageId = uuidFactory.create()
        mockMvc
          .perform(
            get("/client/v1/on_search")
              .param("messageId", nonExistentMessageId)
          )
          .andExpect(status().isNotFound)
          .andExpect(jsonPath("$.error.code", `is`("BAP_008")))
          .andExpect(jsonPath("$.error.message", `is`("No message with the given ID")))
      }

      it("should return search response when message id is valid") {
        val message = messageRepository.insertOne(Message(id = uuidFactory.create(), type = Message.Type.Search))
        val protocolCatalog1 = catalogFactory.create(index = 1)
        val protocolCatalog2 = catalogFactory.create(index = 2)
        mapToEntityAndPersist(message, protocolCatalog1)
        mapToEntityAndPersist(message, protocolCatalog2)

        val response = mockMvc
          .perform(
            get("/client/v1/on_search")
              .param("messageId", message.id)
          )
          .andExpect(status().isOk)
          .andExpect(jsonPath("$.message.catalogs.length()", `is`(2)))
          .andReturn()
          .response
        val onSearchResponse = objectMapper.readValue(response.contentAsString, ClientSearchResponse::class.java)
        onSearchResponse.message shouldBe ClientSearchResponseMessage(listOf(protocolCatalog1, protocolCatalog2))
      }
    }
  }

  private fun mapToEntityAndPersist(message: Message, protocolCatalog: ProtocolCatalog) {
    val context = contextMapper.fromSchema(contextFactory.create(messageId = message.id))
    val entityCatalog = catalogMapper.schemaToEntity(protocolCatalog)
    searchResponseRepository.insertOne(
      SearchResponse(
        context = context,
        message = SearchResponseMessage(entityCatalog)
      )
    )
  }
}
