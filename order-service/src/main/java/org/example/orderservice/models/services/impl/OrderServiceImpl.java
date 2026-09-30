package org.example.orderservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderDetailResponse;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.example.orderservice.models.entities.Order;
import org.example.orderservice.models.entities.OrderDetail;
import org.example.orderservice.models.repositories.OrderDetailRepository;
import org.example.orderservice.models.repositories.OrderRepository;
import org.example.orderservice.models.services.OrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductGatewayService productGatewayService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. Fetch all products to validate existence and get latest prices
        List<ProductResponse> products = new ArrayList<>();
        for (CreateOrderDetailRequest item : request.items()) {
            ProductResponse product = productGatewayService.getProductById(item.productId());
            products.add(product);
        }

        // 2. Calculate total = sum of (unitPrice * quantity) for each line
        double total = 0.0;
        for (int i = 0; i < request.items().size(); i++) {
            total += products.get(i).price() * request.items().get(i).quantity();
        }

        // 3. Create and save Order with status PENDING
        Order order = Order.builder()
                .customerName(request.customerName())
                .total(total)
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);

        // 4. Create and save OrderDetails, build response list
        List<OrderDetailResponse> orderDetailResponses = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            CreateOrderDetailRequest item = request.items().get(i);
            ProductResponse product = products.get(i);
            double subtotal = product.price() * item.quantity();

            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .productId(product.id())
                    .quantity(item.quantity())
                    .unitPrice(product.price())
                    .build();
            detail = orderDetailRepository.save(detail);

            orderDetailResponses.add(new OrderDetailResponse(
                    detail.getId(),
                    product.id(),
                    product.name(),
                    item.quantity(),
                    product.price(),
                    subtotal
            ));
        }

        // 5. Send Kafka event after successful order creation
        kafkaTemplate.send("order-created", request.customerEmail());

        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getTotal(),
                order.getStatus(),
                orderDetailResponses
        );
    }
}
