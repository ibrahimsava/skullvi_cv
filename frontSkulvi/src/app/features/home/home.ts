import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { RevealDirective } from './reveal.directive';


@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RevealDirective],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {}