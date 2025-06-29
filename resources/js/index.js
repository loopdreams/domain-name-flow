import * as echarts from 'echarts/core';
import 'htmx.org';
import 'htmx-ext-ws';

import { LineChart } from 'echarts/charts';

import {
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  DataZoomComponent
} from 'echarts/components';

import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  LineChart,
  TooltipComponent,
  DataZoomComponent,
  GridComponent,
  DatasetComponent,
  CanvasRenderer
]);


export default echarts;
