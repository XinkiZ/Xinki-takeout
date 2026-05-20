package com.sky.tools;

import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserToolsMapper;
import com.sky.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
@Slf4j
public class UserTools {

    private final UserToolsMapper userToolsMapper;
    private final ShoppingCartMapper shoppingCartMapper;
    private final SetmealDishMapper setmealDishMapper;
    private final SetmealMapper setmealMapper;
    private final ShoppingCartService shoppingCartService;
    private final DishMapper dishMapper;

    @Tool(description = "根据关键字列表搜索菜品，返回匹配的菜品列表（含id、名称、价格、描述）")
    public List<Dish> searchDishByKeyWords(
            @ToolParam(description = "关键字列表，每个表项只含一个汉字或词语") List<String> keyWords) {
        log.info("根据关键字查询菜品：{}", keyWords);
        return userToolsMapper.searchDishByKeyWords(keyWords);
    }

    @Tool(description = "根据关键字列表搜索套餐，返回匹配的套餐列表（含id、名称、价格、描述）")
    public List<Setmeal> searchSetmealByKeyWords(
            @ToolParam(description = "关键字列表，每个表项只含一个汉字或词语") List<String> keyWords) {
        log.info("根据关键字查询套餐：{}", keyWords);
        return userToolsMapper.searchSetmealByKeyWords(keyWords);
    }

    @Tool(description = "优化购物车：检查购物车中散点菜品是否能替换为更优惠的套餐。" +
            "返回的JSON中如果canOptimize=true，请告知用户优化方案并询问是否采用。" +
            "setmealId是纯数字，直接传给applyOptimization即可")
    public String optimizeCart() {
        Long userId = BaseContext.getCurrentId();
        log.info("优化购物车，userId={}", userId);

        List<ShoppingCart> cartItems = shoppingCartMapper.list(
                ShoppingCart.builder().userId(userId).build());

        if (cartItems.isEmpty()) {
            return "{\"canOptimize\":false,\"message\":\"购物车为空\"}";
        }

        List<ShoppingCart> dishItems = cartItems.stream()
                .filter(item -> item.getDishId() != null)
                .toList();

        if (dishItems.isEmpty()) {
            return "{\"canOptimize\":false,\"message\":\"购物车中没有散点菜品，只有套餐，无需优化\"}";
        }

        Map<Long, Integer> cartDishQtyMap = new HashMap<>();
        Map<Long, BigDecimal> cartDishPriceMap = new HashMap<>();
        BigDecimal cartTotal = BigDecimal.ZERO;
        for (ShoppingCart item : dishItems) {
            cartDishQtyMap.merge(item.getDishId(), item.getNumber(), Integer::sum);
            cartDishPriceMap.putIfAbsent(item.getDishId(), item.getAmount());
            cartTotal = cartTotal.add(item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
        }

        List<Long> dishIds = new ArrayList<>(cartDishQtyMap.keySet());
        List<Long> candidateSetmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);

        if (candidateSetmealIds == null || candidateSetmealIds.isEmpty()) {
            return "{\"canOptimize\":false,\"message\":\"未找到包含这些菜品的套餐\"}";
        }

        StringBuilder plans = new StringBuilder();
        plans.append("{\"canOptimize\":true,\"cartTotal\":").append(cartTotal).append(",\"plans\":[");

        boolean hasPlan = false;
        for (Long setmealId : candidateSetmealIds) {
            Setmeal setmeal = setmealMapper.getById(setmealId);
            if (setmeal == null || setmeal.getStatus() != StatusConstant.ENABLE) {
                continue;
            }

            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(setmealId);
            Map<Long, Integer> setmealDishQtyMap = new HashMap<>();
            for (SetmealDish sd : setmealDishes) {
                setmealDishQtyMap.merge(sd.getDishId(), sd.getCopies(), Integer::sum);
            }

            int maxSets = Integer.MAX_VALUE;
            for (Map.Entry<Long, Integer> entry : setmealDishQtyMap.entrySet()) {
                Integer cartQty = cartDishQtyMap.get(entry.getKey());
                if (cartQty == null) {
                    maxSets = 0;
                    break;
                }
                maxSets = Math.min(maxSets, cartQty / entry.getValue());
            }

            if (maxSets <= 0) {
                continue;
            }

            BigDecimal setmealSubtotal = setmeal.getPrice().multiply(BigDecimal.valueOf(maxSets));
            BigDecimal remainingTotal = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> entry : cartDishQtyMap.entrySet()) {
                Long dId = entry.getKey();
                int cartQty = entry.getValue();
                int consumed = maxSets * setmealDishQtyMap.getOrDefault(dId, 0);
                int remaining = cartQty - consumed;
                if (remaining > 0) {
                    remainingTotal = remainingTotal.add(
                            cartDishPriceMap.get(dId).multiply(BigDecimal.valueOf(remaining)));
                }
            }
            BigDecimal optimizedTotal = setmealSubtotal.add(remainingTotal);
            BigDecimal savedAmount = cartTotal.subtract(optimizedTotal);

            if (savedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (hasPlan) plans.append(",");
            hasPlan = true;

            plans.append("{\"setmealId\":").append(setmealId).append(",");
            plans.append("\"setmealName\":\"").append(setmeal.getName()).append("\",");
            plans.append("\"setmealPrice\":").append(setmeal.getPrice()).append(",");
            plans.append("\"maxSets\":").append(maxSets).append(",");
            plans.append("\"optimizedTotal\":").append(optimizedTotal).append(",");
            plans.append("\"savedAmount\":").append(savedAmount).append("}");
        }

        plans.append("]}");

        if (!hasPlan) {
            return "{\"canOptimize\":false,\"message\":\"当前散点菜品总价" + cartTotal
                    + "元，已是最优方案\"}";
        }

        log.info("优化购物车结果：{}", plans);
        return plans.toString();
    }

    @Tool(description = "应用购物车优化：传入optimizeCart返回JSON中的setmealId（纯数字），自动计算份数并执行替换")
    public String applyOptimization(
            @ToolParam(description = "套餐ID，取optimizeCart返回JSON中plans[0].setmealId的数值") Long setmealId) {
        Long userId = BaseContext.getCurrentId();
        log.info("应用优化，userId={}, setmealId={}", userId, setmealId);

        Setmeal setmeal = setmealMapper.getById(setmealId);
        if (setmeal == null) {
            return "套餐不存在（id=" + setmealId + "），应用优化失败。";
        }
        if (setmeal.getStatus() != StatusConstant.ENABLE) {
            return "套餐「" + setmeal.getName() + "」已停售，无法应用优化。";
        }

        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(setmealId);
        Map<Long, Integer> setmealDishQtyMap = new HashMap<>();
        for (SetmealDish sd : setmealDishes) {
            setmealDishQtyMap.merge(sd.getDishId(), sd.getCopies(), Integer::sum);
        }
        log.info("套餐包含菜品及份数：{}", setmealDishQtyMap);

        List<ShoppingCart> cartItems = shoppingCartMapper.list(
                ShoppingCart.builder().userId(userId).build());

        Map<Long, Integer> cartDishQtyMap = new HashMap<>();
        for (ShoppingCart item : cartItems) {
            if (item.getDishId() != null) {
                cartDishQtyMap.merge(item.getDishId(), item.getNumber(), Integer::sum);
            }
        }

        int maxSets = Integer.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : setmealDishQtyMap.entrySet()) {
            Integer cartQty = cartDishQtyMap.get(entry.getKey());
            if (cartQty == null || cartQty < entry.getValue()) {
                maxSets = 0;
                break;
            }
            maxSets = Math.min(maxSets, cartQty / entry.getValue());
        }

        if (maxSets <= 0) {
            return "购物车中菜品数量不足以组成一份该套餐，无法应用优化。";
        }
        log.info("套餐可组成 {} 份", maxSets);

        Map<Long, Integer> toReduce = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : setmealDishQtyMap.entrySet()) {
            toReduce.put(entry.getKey(), maxSets * entry.getValue());
        }
        log.info("需减少的菜品数量：{}", toReduce);

        int removedCount = 0;
        for (ShoppingCart item : cartItems) {
            if (item.getDishId() == null) continue;
            Integer reduceQty = toReduce.get(item.getDishId());
            if (reduceQty == null || reduceQty <= 0) continue;

            int cartQty = item.getNumber();
            if (cartQty > reduceQty) {
                item.setNumber(cartQty - reduceQty);
                shoppingCartMapper.updateNumberById(item);
                log.info("减少购物车菜品：菜品={}, dishId={}, {} → {}",
                        item.getName(), item.getDishId(), cartQty, cartQty - reduceQty);
                removedCount += reduceQty;
                toReduce.put(item.getDishId(), 0);
            } else {
                log.info("移除购物车项：id={}, 菜品={}, dishId={}, 数量={}",
                        item.getId(), item.getName(), item.getDishId(), cartQty);
                shoppingCartMapper.deleteById(item.getId());
                removedCount += cartQty;
                toReduce.put(item.getDishId(), reduceQty - cartQty);
            }
        }

        ShoppingCartDTO setmealDTO = new ShoppingCartDTO();
        setmealDTO.setSetmealId(setmealId);
        for (int i = 0; i < maxSets; i++) {
            shoppingCartService.addShoppingCart(setmealDTO);
        }
        log.info("已添加 {} 份套餐「{}」到购物车", maxSets, setmeal.getName());

        BigDecimal totalSetmealPrice = setmeal.getPrice().multiply(BigDecimal.valueOf(maxSets));
        return "优化成功！移除了 " + removedCount + " 份散点菜品，替换为 "
                + maxSets + " 份套餐「" + setmeal.getName()
                + "」（共 " + totalSetmealPrice + " 元）。";
    }

    @Tool(description = "向购物车添加菜品。必须先用searchDishByKeyWords搜索获取菜品id，再调用此工具")
    public String addDishToCart(
            @ToolParam(description = "菜品ID，从searchDishByKeyWords返回结果中获取") Long dishId,
            @ToolParam(description = "口味（可选），如'微辣''不辣'", required = false) String dishFlavor) {
        log.info("添加菜品到购物车，dishId={}, flavor={}", dishId, dishFlavor);

        Dish dish = dishMapper.getById(dishId);
        if (dish == null) {
            log.warn("菜品不存在：dishId={}", dishId);
            return "菜品不存在（id=" + dishId + "），添加失败。请先用searchDishByKeyWords搜索获取正确的菜品id。";
        }
        if (dish.getStatus() != StatusConstant.ENABLE) {
            log.warn("菜品已停售：{}", dish.getName());
            return "菜品「" + dish.getName() + "」已停售，无法添加。";
        }

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);
        dto.setDishFlavor((dishFlavor != null && !dishFlavor.isEmpty()) ? dishFlavor : null);
        shoppingCartService.addShoppingCart(dto);

        String flavorText = (dishFlavor != null && !dishFlavor.isEmpty())
                ? "（" + dishFlavor + "）" : "";
        String result = "已将「" + dish.getName() + "」" + flavorText + "加入购物车，单价"
                + dish.getPrice() + "元。";
        log.info(result);
        return result;
    }

    @Tool(description = "向购物车添加套餐。必须先用searchSetmealByKeyWords搜索获取套餐id，再调用此工具")
    public String addSetmealToCart(
            @ToolParam(description = "套餐ID，从searchSetmealByKeyWords返回结果中获取") Long setmealId) {
        log.info("添加套餐到购物车，setmealId={}", setmealId);

        Setmeal setmeal = setmealMapper.getById(setmealId);
        if (setmeal == null) {
            log.warn("套餐不存在：setmealId={}", setmealId);
            return "套餐不存在（id=" + setmealId + "），添加失败。请先用searchSetmealByKeyWords搜索获取正确的套餐id。";
        }
        if (setmeal.getStatus() != StatusConstant.ENABLE) {
            log.warn("套餐已停售：{}", setmeal.getName());
            return "套餐「" + setmeal.getName() + "」已停售，无法添加。";
        }

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setSetmealId(setmealId);
        shoppingCartService.addShoppingCart(dto);

        String result = "已将套餐「" + setmeal.getName() + "」加入购物车，价格"
                + setmeal.getPrice() + "元。";
        log.info(result);
        return result;
    }
}
